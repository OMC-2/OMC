import os
import re

files = {
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\application\service\RaffleAppServiceTest.java': [
        (r'\"bk_\" \+ UUID.randomUUID\(\).toString\(\),\s*UUID.randomUUID\(\),', r'UUID.randomUUID(), "bk_" + UUID.randomUUID().toString(),')
    ],
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\application\service\RaffleConcurrencyTest.java': [
        (r'\"bk_\" \+ UUID.randomUUID\(\).toString\(\),\s*UUID.randomUUID\(\),', r'UUID.randomUUID(), "bk_" + UUID.randomUUID().toString(),')
    ],
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\infrastructure\client\PaymentClientTest.java': [
        (r'String billingKeyId = UUID.randomUUID\(\);', r'String billingKeyId = "bk_" + UUID.randomUUID().toString();')
    ],
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\presentation\controller\RaffleControllerTest.java': [
        (r'String billingKeyId = UUID.randomUUID\(\);', r'String billingKeyId = "bk_" + UUID.randomUUID().toString();')
    ]
}

for file_path, replacements in files.items():
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    for old, new in replacements:
        content = re.sub(old, new, content)
        
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
