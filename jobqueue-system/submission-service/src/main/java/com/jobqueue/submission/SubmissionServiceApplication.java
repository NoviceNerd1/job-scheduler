//package com.submission.submission;
//
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//
//@SpringBootApplication
//public class SubmissionServiceApplication {
//
//	public static void main(String[] args) {
//		SpringApplication.run(SubmissionServiceApplication.class, args);
//	}
//
//}


package com.jobqueue.submission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
@RestController
public class SubmissionServiceApplication {

	@Autowired
	private DataSource dataSource;

	public static void main(String[] args) {
		SpringApplication.run(SubmissionServiceApplication.class, args);
	}

	@GetMapping("/")
	public Map<String, String> home() {
		Map<String, String> response = new HashMap<>();
		response.put("service", "submission-service");
		response.put("status", "running");
		response.put("port", "8081");
		return response;
	}

	@GetMapping("/test-db")
	public Map<String, Object> testDb() {
		Map<String, Object> response = new HashMap<>();
		try (Connection conn = dataSource.getConnection()) {
			ResultSet rs = conn.createStatement().executeQuery("SELECT 1 as test, version() as version");
			rs.next();
			response.put("success", true);
			response.put("message", "Database connected!");
			response.put("testQuery", rs.getInt("test"));
			response.put("version", rs.getString("version"));
		} catch (Exception e) {
			response.put("success", false);
			response.put("error", e.getMessage());
		}
		return response;
	}
}