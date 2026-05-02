package com.jobqueue.submission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import java.sql.Connection;
import java.sql.ResultSet;

@SpringBootApplication
@RestController
public class SubmissionServiceApplication {

	@Autowired
	private DataSource dataSource;

	public static void main(String[] args) {
		SpringApplication.run(SubmissionServiceApplication.class, args);
	}

	@GetMapping("/")
	public String home() {
		return "Submission Service is running!";
	}

	@GetMapping("/test-db")
	public String testDb() {
		try (Connection conn = dataSource.getConnection()) {
			ResultSet rs = conn.createStatement().executeQuery("SELECT 1 as test");
			rs.next();
			return "✅ Database connected! Test query result: " + rs.getInt("test");
		} catch (Exception e) {
			return "❌ Database connection failed: " + e.getMessage();
		}
	}
}