package uyun.indian.tester;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;
import uyun.whale.common.util.system.WaitCtrlC;

@SpringBootApplication
@ComponentScan(basePackages = {"uyun.indian.tester"})
@ImportResource({"classpath:/uyun/indian/tester/spring.xml"})
public class IndianTestingApp {

    static ConfigurableApplicationContext ctx;
    private static final Logger logger = LoggerFactory.getLogger(IndianTestingApp.class);

    public static void main(String[] args) {
        try {
            SpringApplication application = new SpringApplication(IndianTestingApp.class);
            application.setWebEnvironment(false);
            application.setBannerMode(Banner.Mode.OFF);
            ctx = application.run(args);
        } catch (Exception err) {
            logger.warn("Main class occur error:", err);
        }
        System.out.println("指标库性能测试开始...");
        Testing testing = ctx.getBeanFactory().getBean(Testing.class);
        testing.push();
        testing.queryLast();
        testing.query();

        WaitCtrlC.start();
    }
}
