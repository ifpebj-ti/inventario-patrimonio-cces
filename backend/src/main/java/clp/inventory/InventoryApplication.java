package clp.inventory;

// Importa a classe SpringApplication do Spring Boot, que é usada para inicializar e executar uma aplicação Spring.
import org.springframework.boot.SpringApplication;
// Importa a anotação SpringBootApplication do Spring Boot, que combina outras anotações
// para simplificar a configuração de uma aplicação Spring Boot.
import org.springframework.boot.autoconfigure.SpringBootApplication;
// Importa a anotação EnableAsync do Spring Framework, que permite o uso de métodos assíncronos.
import org.springframework.scheduling.annotation.EnableAsync;

// Anotação que marca a classe principal de uma aplicação Spring Boot.
// Ela é uma combinação de:
// - @Configuration: Marca a classe como uma fonte de definições de beans para o contexto da aplicação.
// - @EnableAutoConfiguration: Habilita a configuração automática do Spring Boot, que tenta configurar
//   sua aplicação com base nas dependências de classpath que você adicionou.
// - @ComponentScan: Habilita a varredura de componentes para encontrar outros componentes,
//   configurações e serviços na package atual e em suas sub-packages.
@SpringBootApplication
// Anotação que ativa o suporte assíncrono do Spring.
// Com esta anotação, você pode usar @Async em métodos de serviço para executá-los em um thread separado,
// sem bloquear o thread principal da requisição, o que é útil para operações demoradas como envio de e-mails.
@EnableAsync
public class InventoryApplication {

    /**
     * Método principal que serve como ponto de entrada para a aplicação Spring Boot.
     *
     * @param args Argumentos de linha de comando passados para a aplicação.
     */
    public static void main(String[] args) {
        // Inicia a aplicação Spring Boot.
        // SpringApplication.run() inicializa o contexto Spring, varre os componentes,
        // configura o servidor embarcado (como Tomcat, por padrão) e implanta a aplicação.
        SpringApplication.run(InventoryApplication.class, args);
    }

}