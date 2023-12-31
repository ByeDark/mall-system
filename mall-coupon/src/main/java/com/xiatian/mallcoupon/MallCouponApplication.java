package com.xiatian.mallcoupon;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.openfeign.EnableFeignClients;

//RefreshScope默认刷新
@SpringBootApplication
@MapperScan("com.xiatian.mallcoupon.mapper")
@EnableFeignClients(basePackages = "com.xiatian.mallcoupon.feign")
public class MallCouponApplication {

	public static void main(String[] args) {
		SpringApplication.run(MallCouponApplication.class, args);
	}

}
