package com.lorne.tx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@EnableEurekaServer
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class})
public class TxManagerApplication {

	/**
	 * run 方式运行时请注释掉 pom文件标记的run 方式时需要注释 war 的包
	 *
	 */

	public static void main(String[] args) {
		SpringApplication.run(TxManagerApplication.class, args);
	}

}
