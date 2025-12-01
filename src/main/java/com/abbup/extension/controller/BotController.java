package com.abbup.extension.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.abbup.extension.service.BotService;

@RestController
public class BotController {
	
	@Autowired
	private RestTemplate restTemplate;
	
	@Autowired
	private BotService botService;
	
	@GetMapping("/health")
    public String healthCheck() {
        return "✅ DeadlineGuard is Running!";
    }
	
    @PostMapping("/api/bot")
    public void handleCliqEvent(@RequestBody Map<String, Object> payload) {
    	String responseUrl = (String) payload.get("response_url");
    	Map<String, Object> actionData = (Map<String, Object>) payload.get("handler");
    	String actionName = null, arguments = null, userId = null;
    	System.out.println(actionData);
    	
    	if(actionData.get("type").equals("message_handler")) return;
    	
    	boolean isWelcome = "welcome_handler".equals(actionData.get("type"));

		if (isWelcome) {
			System.out.println("👋 Welcome Event Detected!");
			if (responseUrl != null) {
			    Map<String, Object> manual = botService.generateWelcomeMessage();
			    
			    
			    try {
			    	Map<String, Object> finalPayload = new HashMap<>();
			        finalPayload.put("output", manual);
			        
			        restTemplate.postForObject(responseUrl, finalPayload, String.class);
			        System.out.println("✅ Welcome Message Sent.");
			    } catch (Exception e) {
			        System.err.println("❌ Welcome Error: " + e.getMessage());
			    }
			}
			return;
		}
    	
    	else {
	        actionName = (String) actionData.get("name");
	        
	        if(actionData.get("name") != null) actionName = (String) actionData.get("name");
	        else actionName = (String) payload.get("name");
	        
	        System.out.println(actionName);
	        if(actionName.equalsIgnoreCase("createtask") || actionName.equalsIgnoreCase("updatetask") || 
	        		actionName.equalsIgnoreCase("setup") || actionName.equalsIgnoreCase("StopMonitor") ||
	        		actionName.equalsIgnoreCase("StartMonitor")) {
	        	Map<?, ?> params = (Map<?, ?>) payload.get("params");
	        	arguments = (String) params.get("arguments");
	        	
	        	if(actionName.equalsIgnoreCase("setup") || actionName.equalsIgnoreCase("StopMonitor") ||
	        			actionName.equalsIgnoreCase("StartMonitor")) {
	    			Map<?, ?> user = (Map<?, ?>) params.get("user");
	    			userId = (String) user.get("zoho_user_id");
	        	}
	        }
	    	
	    	Map<String, Object> outputMap = switch(actionName) {
	    		case "CheckStatus" -> botService.generateHealthReport();
	    		case "CriticalList" -> botService.generateCriticalTaskList();
	    		case "TaskList" -> botService.generateAllTaskList();
	    		case "StartMonitor" -> botService.handleStartMonitor(userId);
	    		case "StopMonitor" -> botService.handleStopMonitor(userId);
	    		case "createtask" -> botService.handleCreateTaskCommand(arguments);
	    		case "updatetask" -> botService.handleUpdateTaskCommand(arguments);
	    		case "setup" -> botService.handleSetupCommand(arguments, userId);
	    		default -> null;
	    	};
	        
	        Map<String, Object> finalPayload = new HashMap<>();
	        finalPayload.put("output", outputMap);
	        
	        restTemplate.postForObject(responseUrl, finalPayload, String.class);
	        System.out.println("✅ Message sent successfully to response_url.");
	    }
    }
        
}
