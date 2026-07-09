package com.omc.order.infrastructure.config;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


//Spring AI ChatClient 빈 구성
//spring-ai-starter-model-openai 가 ChatModel(OpenAiChatModel)을 자동으로 구성

@Configuration
public class AiConfig {
  @Bean
  public ChatClient chatClient(ChatModel chatModel){
    return ChatClient.builder(chatModel).build();
  }
}
