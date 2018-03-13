package spring.circular.reference;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;


//https://www.jianshu.com/p/4106321aef79
public class Application {
    public static void main(final String[] args) {
        ApplicationContext context = new AnnotationConfigApplicationContext(ApplicationConfig.class);
    }
}
