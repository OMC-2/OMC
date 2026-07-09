terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.0"
    }
  }
}

provider "aws" {
  region  = var.aws_region
  profile = var.aws_profile
}

variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "aws_profile" {
  type    = string
  default = "terraform-user-my"
}

variable "availability_zone" {
  type    = string
  default = "ap-northeast-2a"
}

variable "edge_instance_type" {
  type    = string
  default = "c7g.medium"
}

variable "was_instance_type" {
  type    = string
  default = "m7g.2xlarge"
}

variable "infra_instance_type" {
  type    = string
  default = "r7g.xlarge"
}

locals {
  common_tags = {
    Project   = "OMC"
    ManagedBy = "Terraform"
  }

  docker_install_user_data = <<-EOF
    #!/bin/bash
    set -euxo pipefail
    apt-get update
    apt-get install -y ca-certificates curl gnupg
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor --yes -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
    . /etc/os-release
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $${VERSION_CODENAME} stable" > /etc/apt/sources.list.d/docker.list
    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    usermod -aG docker ubuntu
    systemctl enable --now docker
  EOF
}

# SSH key shared by the public bastion and both private instances.
resource "tls_private_key" "omc_key" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "omc_key" {
  key_name   = "omc-key"
  public_key = tls_private_key.omc_key.public_key_openssh

  tags = local.common_tags
}

resource "local_file" "omc_key_pem" {
  content         = tls_private_key.omc_key.private_key_pem
  filename        = "${path.module}/omc-key.pem"
  file_permission = "0400"
}

# Network: only the edge host is public. WAS and infra use the NAT gateway for outbound traffic.
resource "aws_vpc" "omc" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(local.common_tags, { Name = "omc-vpc" })
}

resource "aws_internet_gateway" "omc" {
  vpc_id = aws_vpc.omc.id

  tags = merge(local.common_tags, { Name = "omc-igw" })
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.omc.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = var.availability_zone
  map_public_ip_on_launch = true

  tags = merge(local.common_tags, { Name = "omc-public-subnet" })
}

resource "aws_subnet" "private" {
  vpc_id                  = aws_vpc.omc.id
  cidr_block              = "10.0.2.0/24"
  availability_zone       = var.availability_zone
  map_public_ip_on_launch = false

  tags = merge(local.common_tags, { Name = "omc-private-subnet" })
}

resource "aws_eip" "nat" {
  domain = "vpc"

  tags = merge(local.common_tags, { Name = "omc-nat-eip" })

  depends_on = [aws_internet_gateway.omc]
}

resource "aws_nat_gateway" "omc" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public.id

  tags = merge(local.common_tags, { Name = "omc-nat-gateway" })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.omc.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.omc.id
  }

  tags = merge(local.common_tags, { Name = "omc-public-rt" })
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.omc.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.omc.id
  }

  tags = merge(local.common_tags, { Name = "omc-private-rt" })
}

resource "aws_route_table_association" "private" {
  subnet_id      = aws_subnet.private.id
  route_table_id = aws_route_table.private.id
}

# Security groups: public ingress terminates at edge; private hosts accept only required cross-tier traffic.
resource "aws_security_group" "edge" {
  name        = "omc-edge-sg"
  description = "Public edge and bastion access"
  vpc_id      = aws_vpc.omc.id

  ingress {
    description = "SSH bastion"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, { Name = "omc-edge-sg" })
}

resource "aws_security_group" "was" {
  name        = "omc-was-sg"
  description = "Private gateway and application services"
  vpc_id      = aws_vpc.omc.id

  ingress {
    description     = "SSH through edge bastion"
    from_port       = 22
    to_port         = 22
    protocol        = "tcp"
    security_groups = [aws_security_group.edge.id]
  }

  ingress {
    description     = "Nginx to API gateway"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.edge.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, { Name = "omc-was-sg" })
}

resource "aws_security_group" "infra" {
  name        = "omc-infra-sg"
  description = "Private databases, messaging, and monitoring"
  vpc_id      = aws_vpc.omc.id

  ingress {
    description     = "SSH through edge bastion"
    from_port       = 22
    to_port         = 22
    protocol        = "tcp"
    security_groups = [aws_security_group.edge.id]
  }

  dynamic "ingress" {
    for_each = toset(["2181", "5432", "6379", "9092", "9121", "9411"])
    content {
      description     = "WAS to infrastructure service"
      from_port       = tonumber(ingress.value)
      to_port         = tonumber(ingress.value)
      protocol        = "tcp"
      security_groups = [aws_security_group.was.id]
    }
  }

  ingress {
    description     = "Kafka internal listener"
    from_port       = 29092
    to_port         = 29092
    protocol        = "tcp"
    security_groups = [aws_security_group.was.id]
  }

  # Keycloak runs on edge and stores its data in the infrastructure PostgreSQL.
  ingress {
    description     = "Edge Keycloak to PostgreSQL"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.edge.id]
  }

  dynamic "ingress" {
    for_each = toset(["3000", "3100", "9090", "9411"])
    content {
      description     = "Edge reverse proxy to monitoring"
      from_port       = tonumber(ingress.value)
      to_port         = tonumber(ingress.value)
      protocol        = "tcp"
      security_groups = [aws_security_group.edge.id]
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, { Name = "omc-infra-sg" })
}

# Private WAS services call Keycloak and Toss WireMock on the edge host.
resource "aws_security_group_rule" "edge_from_was_keycloak" {
  type                     = "ingress"
  description              = "WAS to Keycloak"
  from_port                = 8180
  to_port                  = 8180
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.was.id
  security_group_id        = aws_security_group.edge.id
}

resource "aws_security_group_rule" "edge_from_was_toss" {
  type                     = "ingress"
  description              = "WAS to Toss WireMock"
  from_port                = 18080
  to_port                  = 18080
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.was.id
  security_group_id        = aws_security_group.edge.id
}

resource "aws_security_group_rule" "was_from_infra_prometheus" {
  type                     = "ingress"
  description              = "Prometheus service scraping"
  from_port                = 8000
  to_port                  = 8999
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.infra.id
  security_group_id        = aws_security_group.was.id
}

data "aws_ami" "ubuntu_arm64" {
  most_recent = true
  owners      = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-arm64-server-*"]
  }

  filter {
    name   = "architecture"
    values = ["arm64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_instance" "edge" {
  ami                         = data.aws_ami.ubuntu_arm64.id
  instance_type               = var.edge_instance_type
  key_name                    = aws_key_pair.omc_key.key_name
  subnet_id                   = aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.edge.id]
  associate_public_ip_address = true
  user_data                   = local.docker_install_user_data

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
    encrypted   = true
  }

  tags = merge(local.common_tags, { Name = "omc-edge-server" })
}

resource "aws_instance" "was" {
  ami                         = data.aws_ami.ubuntu_arm64.id
  instance_type               = var.was_instance_type
  key_name                    = aws_key_pair.omc_key.key_name
  subnet_id                   = aws_subnet.private.id
  vpc_security_group_ids      = [aws_security_group.was.id]
  associate_public_ip_address = false
  user_data                   = local.docker_install_user_data

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_size = 50
    volume_type = "gp3"
    encrypted   = true
  }

  tags = merge(local.common_tags, { Name = "omc-was-server" })
}

resource "aws_instance" "infra" {
  ami                         = data.aws_ami.ubuntu_arm64.id
  instance_type               = var.infra_instance_type
  key_name                    = aws_key_pair.omc_key.key_name
  subnet_id                   = aws_subnet.private.id
  vpc_security_group_ids      = [aws_security_group.infra.id]
  associate_public_ip_address = false
  user_data                   = local.docker_install_user_data

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_size = 100
    volume_type = "gp3"
    encrypted   = true
  }

  tags = merge(local.common_tags, { Name = "omc-infra-server" })
}

resource "aws_eip" "edge" {
  domain   = "vpc"
  instance = aws_instance.edge.id

  tags = merge(local.common_tags, { Name = "omc-edge-eip" })

  depends_on = [aws_internet_gateway.omc]
}

output "edge_elastic_ip" {
  value       = aws_eip.edge.public_ip
  description = "Public edge/bastion IP (GitHub EC2_HOST)"
}

output "edge_private_ip" {
  value       = aws_instance.edge.private_ip
  description = "Edge private IP used by WAS for Keycloak and Toss WireMock"
}

output "was_private_ip" {
  value       = aws_instance.was.private_ip
  description = "Private WAS host IP"
}

output "infra_private_ip" {
  value       = aws_instance.infra.private_ip
  description = "Private database and infrastructure host IP"
}

output "ssh_commands" {
  value = {
    edge  = "ssh -i omc-key.pem ubuntu@${aws_eip.edge.public_ip}"
    was   = "ssh -i omc-key.pem -J ubuntu@${aws_eip.edge.public_ip} ubuntu@${aws_instance.was.private_ip}"
    infra = "ssh -i omc-key.pem -J ubuntu@${aws_eip.edge.public_ip} ubuntu@${aws_instance.infra.private_ip}"
  }
  description = "SSH commands for edge and private hosts"
}
