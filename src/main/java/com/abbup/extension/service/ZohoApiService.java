package com.abbup.extension.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class ZohoApiService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${zoho.client.id}")
    private String clientId;
    @Value("${zoho.client.secret}")
    private String clientSecret;
    @Value("${zoho.refresh.token}")
    private String refreshToken;
    @Value("${zoho.portal.id}")
    private String portalId;
    @Value("${zoho.project.id}")
    private String projectId;

    // Token Cache
    private String cachedAccessToken = null;
    private long tokenExpiryTime = 0;

    private String getAccessToken() {
        // Check Cache (5 min buffer)
        if (cachedAccessToken != null && System.currentTimeMillis() < (tokenExpiryTime - 300000)) {
            return cachedAccessToken;
        }

        System.out.println("🔄 Generating NEW Access Token...");
        String url = "https://accounts.zoho.com/oauth/v2/token";

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("refresh_token", refreshToken);
        map.add("client_id", clientId);
        map.add("client_secret", clientSecret);
        map.add("grant_type", "refresh_token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            Map response = restTemplate.postForObject(url, request, Map.class);
            if (response != null && response.containsKey("access_token")) {
                this.cachedAccessToken = (String) response.get("access_token");
                int expiresIn = Integer.parseInt(String.valueOf(response.get("expires_in")));
                this.tokenExpiryTime = System.currentTimeMillis() + (expiresIn * 1000L);
                return this.cachedAccessToken;
            }
        } catch (Exception e) {
            System.err.println("❌ Token Error: " + e.getMessage());
        }
        return null;
    }

    public void updateTaskPriority(String taskId, String newPriority) {
        String token = getAccessToken();
        if (token == null) return;

        // Using the RESTAPI (V1) endpoint as it proved stable for your account
        String url = "https://projectsapi.zoho.com/restapi/portal/" + portalId + 
                     "/projects/" + projectId + "/tasks/" + taskId + "/";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("priority", newPriority);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForObject(url, request, String.class);
            System.out.println("✅ Successfully updated Task " + taskId + " to " + newPriority);
        } catch (Exception e) {
            System.err.println("❌ Update Failed: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getTasks() {
        String token = getAccessToken();
        if (token == null) return Collections.emptyList();

        String url = "https://projectsapi.zoho.com/api/v3/portal/" + portalId + "/projects/" + projectId + "/tasks";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("tasks")) {
                return (List<Map<String, Object>>) body.get("tasks");
            }
        } catch (Exception e) {
            System.err.println("❌ Fetch Error: " + e.getMessage());
        }
        return Collections.emptyList();
    }
    
    public boolean createTask(String taskName, String dueDate, String priority) {
        String token = getAccessToken();
        if (token == null) return false;

        String url = "https://projectsapi.zoho.com/restapi/portal/" + portalId + 
                     "/projects/" + projectId + "/tasks/";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        
        String finalDate = dueDate;
        // Convert yyyy-MM-dd (HTML input) to MM-dd-yyyy (Zoho API format) if needed
        // Assuming input is yyyy-MM-dd
        try {
            if (dueDate.contains("-")) {
                String[] parts = dueDate.split("-");
                if (parts[0].length() == 4) { // It is YYYY-MM-DD
                    // Convert to MM-dd-yyyy
                    finalDate = parts[1] + "-" + parts[2] + "-" + parts[0];
                    System.out.println("📅 Formatted Date: " + finalDate);
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Date parsing error, sending raw: " + dueDate);
        }
        
        body.add("name", taskName);
        body.add("end_date", finalDate);
        body.add("priority", priority);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            // Send Request
        	ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        	System.out.println("📤 Response: " + response.getStatusCode());
            
            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ Task Created Successfully!");
                return true;
            } else {
                System.err.println("❌ Zoho Failed: " + response.getBody());
                return false;
            }
        } catch (Exception e) {
            System.err.println("❌ Create Task Failed: " + e.getMessage());
            return false;
        }
    }
    
    public boolean updateTaskField(String taskId, String field, String value) {
        String token = getAccessToken();
        if (token == null) return false;

        String url = "https://projectsapi.zoho.com/restapi/portal/" + portalId + 
                     "/projects/" + projectId + "/tasks/" + taskId + "/";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();

        // MAP USER FIELDS TO API KEYS
        switch (field) {
            case "priority":
                body.add("priority", value);
                break;
                
            case "name":
            case "title":
                body.add("name", value);
                break;
                
            case "date":
            case "due_date":
            case "end_date":
                // Handle Date Formatting
                if (value.matches("\\d{4}-\\d{2}-\\d{2}")) { // YYYY-MM-DD
                   String[] p = value.split("-");
                   value = p[1] + "-" + p[2] + "-" + p[0]; // Convert to MM-dd-yyyy
                }
                body.add("end_date", value);
                break;
                
            case "status":
                body.add("status_name", value); 
                break;
                
            default:
                System.out.println("❌ Unknown field: " + field);
                return false;
        }

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForObject(url, request, String.class);
            System.out.println("✅ Task " + taskId + " updated (" + field + ")");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Update Failed: " + e.getMessage());
            return false;
        }
    }
}
