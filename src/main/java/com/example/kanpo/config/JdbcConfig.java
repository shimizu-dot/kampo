package com.example.kanpo.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class JdbcConfig {

	@Bean
	DataSource dataSource(
			@Value("${spring.datasource.driver-class-name:org.postgresql.Driver}") String driverClassName,
			@Value("${spring.datasource.url:}") String url,
			@Value("${spring.datasource.username:}") String username,
			@Value("${spring.datasource.password:}") String password) {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		if (!driverClassName.isBlank()) {
			dataSource.setDriverClassName(driverClassName);
		}
		dataSource.setUrl(url);
		dataSource.setUsername(username);
		dataSource.setPassword(password);
		return dataSource;
	}

	@Bean
	JdbcTemplate jdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}
}
