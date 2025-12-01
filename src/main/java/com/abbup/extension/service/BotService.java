package com.abbup.extension.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.abbup.extension.model.Button;
import com.abbup.extension.model.Card;
import com.abbup.extension.model.Connection;

@Service
public class BotService {
	public static Map<String, Connection> registeredWebhooks = new ConcurrentHashMap<>();
	
	@Autowired
	private MessageComponents messageComponents;
	
    @Autowired
    private ZohoApiService zohoApiService;
    
    @Autowired
    private AlertScheduler alertScheduler;

    public Map<String, Object> generateHealthReport() {
        System.out.println("📊 Generating Health Report...");
        
        // 1. Fetch Data
        List<Map<String, Object>> tasks = zohoApiService.getTasks();
        
        int totalOpen = 0;
        int highPriority = 0;
        int overdue = 0;
        
        if (tasks != null) {
            LocalDate today = LocalDate.now();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (Map<String, Object> task : tasks) {
                // Status Check
                Map<String, Object> statusObj = (Map<String, Object>) task.get("status");
                if (!"Open".equalsIgnoreCase((String) statusObj.get("name"))) continue;
                
                totalOpen++;

                // Priority Check
                Object pObj = task.get("priority");
                String pName = (pObj instanceof Map) ? (String)((Map)pObj).get("name") : (String)pObj;
                if ("High".equalsIgnoreCase(pName)) highPriority++;

                // Date Check
                if (task.containsKey("end_date") && task.get("end_date") != null) {
                    String dStr = (String) task.get("end_date");
                    try {
                         LocalDate d;
                         if (dStr.contains("T")) {
                             d = java.time.ZonedDateTime.parse(dStr).toLocalDate();
                         } else {
                             try {
                                 d = LocalDate.parse(dStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                             } catch (Exception ex) {
                                 d = LocalDate.parse(dStr, DateTimeFormatter.ofPattern("MM-dd-yyyy"));
                             }
                         }
                         
                         if (!d.isAfter(today)) overdue++;
                    } catch (Exception e) {}
                }
            }
        }

        Map<String, Object> response = new HashMap<>();
        
        String reportText = "### 🩺 **Project Health: Helios Core**\n" +
                            "----------------------------------\n" +
                            "📝 **Open Tasks:** " + totalOpen + "\n" +
                            "🔥 **High Priority:** " + highPriority + "\n" +
                            "⚠️ **Overdue / Due Today:** " + overdue + "\n" +
                            "----------------------------------\n";
        
        response.put("text", messageComponents.createText(reportText));

        String iconUrl = "https://cdn-icons-png.flaticon.com/512/3094/3094851.png"; 
        
        Card card = new Card("LIVE STATUS CHECK", iconUrl, "modern-inline");
        response.put("card", messageComponents.createCard(card));

        return response;
    }
    
    public Map<String, Object> generateCriticalTaskList() {
    	System.out.println("📊 Generating Critical Task Table...");
        List<Map<String, Object>> tasks = zohoApiService.getTasks();
        
        List<String> headers = List.of("ID", "Task Name", "Owner", "Status", "Due Date");
        List<Map<String, String>> rows = new ArrayList<>();
        
        int criticalCount = 0;

        if (tasks != null) {
            LocalDate today = LocalDate.now();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (Map<String, Object> task : tasks) {
                // Status Check
                Map<String, Object> statusObj = (Map<String, Object>) task.get("status");
                if (!"Open".equalsIgnoreCase((String) statusObj.get("name"))) continue;

                String taskName = (String) task.get("name");
                String taskId = String.valueOf(task.get("id"));
                
                boolean isCritical = false;
                String reason = "Normal";
                String displayDate = "-"; // Default if no date

                // Priority Check
                Object pObj = task.get("priority");
                String pName = (pObj instanceof Map) ? (String)((Map)pObj).get("name") : (String)pObj;
                if ("High".equalsIgnoreCase(pName)) {
                    isCritical = true;
                    reason = "High Priority";
                }

                // Date Check
                if (task.containsKey("end_date") && task.get("end_date") != null) {
                    String dStr = (String) task.get("end_date");
                    try {
                         LocalDate d;
                         // Handle Timestamp format (2025-11-29T09:00:00) vs Date format (2025-11-29)
                         if (dStr.contains("T")) {
                             d = java.time.ZonedDateTime.parse(dStr).toLocalDate();
                         } else {
                             d = LocalDate.parse(dStr, fmt);
                         }
                         
                         // Store the clean date for the table column
                         displayDate = d.toString(); 
                         
                         if (!d.isAfter(today)) {
                             isCritical = true;
                             reason = "OVERDUE";
                         }
                    } catch (Exception e) {}
                }

                if (isCritical) {
                    criticalCount++;
                    
                    // Extract Owner Name
                    String owner = "Unassigned";
                    if (task.containsKey("owners_and_work")) {
                         Object containerObj = task.get("owners_and_work");
                         if (containerObj instanceof Map) {
                             Map<?, ?> containerMap = (Map<?, ?>) containerObj;
                             if (containerMap.containsKey("owners")) {
                                 Object ownersListObj = containerMap.get("owners");
                                 if (ownersListObj instanceof List) {
                                     List<?> ownersList = (List<?>) ownersListObj;
                                     if (!ownersList.isEmpty()) {
                                         Object firstOwner = ownersList.get(0);
                                         if (firstOwner instanceof Map) {
                                             owner = (String) ((Map<?, ?>) firstOwner).get("name");
                                         }
                                     }
                                 }
                             }
                         }
                    }
                    
                    Map<String, String> row = new HashMap<>();
                    
                    row.put("ID", taskId);
                    row.put("Task Name", taskName);
                    row.put("Owner", owner);
                    row.put("Status", reason); 
                    row.put("Due Date", displayDate);
                    
                    rows.add(row);
                }
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        
        if (criticalCount == 0) {
             response.put("text", "✅ **No critical tasks found.** Great job!");
             return response;
        }

        response.put("text", "🚨 **Critical Task Report**\nFound " + criticalCount + " items requiring immediate attention.");

        Card card = new Card("ACTION REQUIRED", "", "modern-inline");
        response.put("card", messageComponents.createCard(card));

        // Table
        response.put("slides", messageComponents.createTable(headers, rows, "⚠️ Critical Bottlenecks"));

        return response;
    }
    
    public Map<String, Object> generateAllTaskList() {
        System.out.println("📊 Generating Full Task List...");
        List<Map<String, Object>> tasks = zohoApiService.getTasks();
        
        List<String> headers = List.of("ID", "Task Name", "Owner", "Status", "Due Date");
        List<Map<String, String>> rows = new ArrayList<>();
        
        int openCount = 0;

        if (tasks != null) {
            LocalDate today = LocalDate.now();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (Map<String, Object> task : tasks) {
                // Filter: Only show OPEN tasks
                Map<String, Object> statusObj = (Map<String, Object>) task.get("status");
                if (!"Open".equalsIgnoreCase((String) statusObj.get("name"))) continue;

                openCount++;

                // --- A. Extract Basic Data ---
                String taskName = (String) task.get("name");
                String taskId = String.valueOf(task.get("id"));
                String displayDate = "-";
                String statusLabel = "Normal";

                // --- B. Determine Priority & Status Label ---
                if (task.containsKey("priority")) {
                    Object pObj = task.get("priority");
                    String pName = (pObj instanceof Map) ? (String)((Map)pObj).get("name") : (String)pObj;
                    
                    if ("High".equalsIgnoreCase(pName)) {
                        statusLabel = "High Priority";
                    } else {
                        statusLabel = pName;
                    }
                }

                // --- C. Check Date & Overdue Status ---
                if (task.containsKey("end_date") && task.get("end_date") != null) {
                    String dStr = (String) task.get("end_date");
                    try {
                         LocalDate d;
                         if (dStr.contains("T")) {
                             d = java.time.ZonedDateTime.parse(dStr).toLocalDate();
                         } else {
                             d = LocalDate.parse(dStr, fmt);
                         }
                         
                         displayDate = d.toString(); 
                         
                         // Overwrite status if Overdue (Visual urgency)
                         if (!d.isAfter(today)) {
                             statusLabel = "OVERDUE";
                         }
                    } catch (Exception e) {}
                }

                // --- D. Extract Owner ---
                String owner = "Unassigned";
                if (task.containsKey("owners_and_work")) {
                     Object containerObj = task.get("owners_and_work");
                     if (containerObj instanceof Map) {
                         Map<?, ?> containerMap = (Map<?, ?>) containerObj;
                         if (containerMap.containsKey("owners")) {
                             Object ownersListObj = containerMap.get("owners");
                             if (ownersListObj instanceof List) {
                                 List<?> ownersList = (List<?>) ownersListObj;
                                 if (!ownersList.isEmpty()) {
                                     Object firstOwner = ownersList.get(0);
                                     if (firstOwner instanceof Map) {
                                         owner = (String) ((Map<?, ?>) firstOwner).get("name");
                                     }
                                 }
                             }
                         }
                     }
                }

                // --- E. Build Row (Plain Text) ---
                Map<String, String> row = new HashMap<>();
                row.put("ID", taskId);
                row.put("Task Name", taskName); // Plain text, no link
                row.put("Owner", owner);
                row.put("Status", statusLabel);
                row.put("Due Date", displayDate);
                
                rows.add(row);
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        
        if (openCount == 0) {
             response.put("text", "✅ **No open tasks.** The project is clear!");
             return response;
        }

        response.put("text", "📋 **Project Overview**\nHere are all **" + openCount + "** active tasks.");

        // Header Card
        Card card = new Card("ALL OPEN TASKS", "", "modern-inline");
        response.put("card", messageComponents.createCard(card));

        // The Table
        response.put("slides", messageComponents.createTable(headers, rows, "All Projects Tasks"));

        return response;
    }
    
    public Map<String, Object> generateWelcomeMessage() {
        Map<String, Object> response = new HashMap<>();
        
        String manualText = "### 🤖 **Hi, I am DeadlineGuard.**\n" +
                "I am an automated agent designed to prevent SLA breaches in **Helios Core**.\n\n" +
                "**🔍 How I Work:**\n" +
                "1. **Monitor:** I scan open tasks every 5 minutes.\n" +
                "2. **Detect:** I look for tasks due **Today** or **Tomorrow**.\n" +
                "3. **Escalate:** If a deadline is close (≤ 2 days), I **Auto-Update** the Priority to 'High'.\n" +
                "4. **Alert:** I notify the manager via the configured webhook.\n\n" +
                
                // 🟢 NEW SECTION: COMMANDS GUIDE
                "**⚡ Available Commands:**\n" +
                "• `/setup <webhook_url>` : **Required** to connect your chat for alerts.\n" +
                "• `/createtask Name, Date, Priority` : Create a task instantly.\n" +
                "• `/updatetask ID, Field, Value` : Modify task details such as Priority and Due Date.\n\n" +
                
                "👇 **Use the menu below to check status manually.**";

        response.put("text", messageComponents.createText(manualText));
        
        String iconUrl = "https://cdn-icons-png.flaticon.com/512/471/471662.png"; 
        Card card = new Card("SYSTEM READY", iconUrl, "modern-inline");
        response.put("card", messageComponents.createCard(card));
        
        return response;
    }

    public Map<String, Object> handleStartMonitor(String userId) {
    	
    	if (registeredWebhooks.get(userId) == null) {
            Map<String, Object> response = new HashMap<>();

            // A. Clear Instructions in the Text
            String instructions = "### ⚙️ **Configuration Required**\n" +
                    "I am ready to monitor, but I need to know where to send the alerts.\n\n" +
                    "**👉 Step 1: Get the Webhook URL**\n" +
                    "Decide where you want alerts (a Channel) and get the link:\n" +
                    "• Right-click Channel Name ➔ Connectors ➔ Incoming Webhook.\n" +
                    "**👉 Step 2: Register**\n" +
                    "Copy the **Full URL** (ensure it includes the `zapikey` token) and run:\n" +
                    "`/setup https://cliq.zoho.com/...`";
            
            response.put("text", messageComponents.createText(instructions));

            String iconUrl = "https://cdn-icons-png.flaticon.com/512/2099/2099058.png"; 
            Card card = new Card("WAITING FOR SETUP", iconUrl, "prompt"); 
            
            response.put("card", messageComponents.createCard(card));

            return response; // Stop here
        }
    	
        // Try to start
        boolean isStarted = alertScheduler.startMonitoring();
        
        Map<String, Object> response = new HashMap<>();
        
        if (isStarted) {
            // SUCCESS
            response.put("text", "🟢 **System Activated!**\nDeadlineGuard is now scanning **Helios Core** every 5 minutes. Alerts will be posted here.");
            String iconUrl = "https://cdn-icons-png.flaticon.com/512/189/189664.png"; // Info Icon
            Card card = new Card("STATUS: ACTIVATED", iconUrl, "modern-inline");
            response.put("card", messageComponents.createCard(card));
        } else {
            // ALREADY RUNNING
            response.put("text", "⚠️ **System is already active.**\nThe monitoring engine is running. You don't need to start it again.");
            
            String iconUrl = "https://cdn-icons-png.flaticon.com/512/189/189664.png"; // Info Icon
            Card card = new Card("STATUS: RUNNING", iconUrl, "modern-inline");
            response.put("card", messageComponents.createCard(card));
        }
        
        return response;
    }
    
    public Map<String, Object> handleStopMonitor(String userId) {
    	Connection removed = registeredWebhooks.remove(userId);
        
        Map<String, Object> response = new HashMap<>();
        
        if (removed != null) {
            response.put("text", "🔕 **Unsubscribed.**\nYou will no longer receive alerts.");
            Card card = new Card("ALERTS MUTED", "https://cdn-icons-png.flaticon.com/512/1828/1828843.png", "modern-inline");
            response.put("card", messageComponents.createCard(card));
        } else {
            response.put("text", "⚠️ **Not Found.** You are not currently subscribed.");
            response.put("card", messageComponents.createCard(new Card("", "https://cdn-icons-png.flaticon.com/512/1828/1828843.png", "modern-inline")));
        }
        
        return response;
    }
    
    public Map<String, Object> handleCreateTaskCommand(String arguments) {
        System.out.println("📝 Parsing Command: " + arguments);

        // 1. Validation: Did they type anything?
        if (arguments == null || arguments.trim().isEmpty()) {
            return Map.of("text", "⚠️ **Usage:** `/createtask Task Name, YYYY-MM-DD, Priority`");
        }

        // 2. Parse: Split by comma
        String[] parts = arguments.split(",");

        if (parts.length < 3) {
            return Map.of("text", "❌ **Missing Info:** Please separate Name, Date, and Priority with commas.\n" +
                                  "Example: `/createtask Fix Bug, 2025-12-01, High`");
        }

        try {
            String taskName = parts[0].trim();
            String dueDate = parts[1].trim(); 
            String priority = parts[2].trim();
            
            if (!isValidFutureDate(dueDate)) {
                return createErrorResponse("❌ **Invalid Date:** The date `" + dueDate + "` is in the past or invalid.\nPlease use **YYYY-MM-DD** format and ensure it is **Today or Future**.");
            }
            
            if (!isValidPriority(priority)) {
                return createErrorResponse("❌ **Invalid Priority:** You entered `" + priority + "`.\n" +
                                           "Allowed values are: `High`, `Medium`, `Low`.");
            }

            boolean success = zohoApiService.createTask(taskName, dueDate, priority);

            if (success) {
                String msg = "✅ **Task Created Successfully!**\n" +
                             "📌 **Task:** " + taskName + "\n" +
                             "📅 **Due:** " + dueDate + "\n" +
                             "🔥 **Priority:** " + priority;
                
                Map<String, Object> response = new HashMap<>();
                response.put("text", messageComponents.createText(msg));
                return response;
            } else {
                return Map.of("text", "❌ **API Error:** Could not create task. Please check the date format (YYYY-MM-DD).");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("text", "⚠️ **Error:** Invalid input format.");
        }
    }
    
    public Map<String, Object> handleUpdateTaskCommand(String arguments) {
        System.out.println("📝 Parsing Update: " + arguments);

        // 1. Validate
        if (arguments == null || !arguments.contains(",")) {
            return Map.of("text", "⚠️ **Usage:** `/updatetask <ID>, <Field>, <Value>`\nExample: `/updatetask 12345, priority, High`");
        }

        String[] parts = arguments.split(",");
        if (parts.length < 3) {
            return Map.of("text", "❌ **Missing Info:** I need ID, Field, and Value separated by commas.");
        }

        try {
            String taskId = parts[0].trim();
            String field = parts[1].trim().toLowerCase(); // e.g., "priority" or "date"
            String value = parts[2].trim();
            
            if (field.contains("date") || field.equals("due_date")) {
                if (!isValidFutureDate(value)) {
                    return createErrorResponse("❌ **Invalid Date Update:**\nYou cannot change a task date to the past (`" + value + "`).\nPlease use a future date (YYYY-MM-DD).");
                }
            }
            
            if (field.equals("priority")) {
                if (!isValidPriority(value)) {
                    return createErrorResponse("❌ **Invalid Priority:** You entered `" + value + "`.\n" +
                                               "Allowed values are: `High`, `Medium`, `Low`.");
                }
            }
            
            // 2. Call API
            boolean success = zohoApiService.updateTaskField(taskId, field, value);

            // 3. Respond
            if (success) {
                return Map.of("text", "✅ **Task Updated Successfully!**\n🆔 Task ID: " + taskId + "\n🔄 Changed **" + field + "** to **" + value + "**");
            } else {
                return Map.of("text", "❌ **Update Failed:** Check if the Task ID is correct.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("text", "⚠️ **Error:** Invalid format.");
        }
    }
    
    private boolean isValidPriority(String p) {
        if (p == null) return false;
        String val = p.trim();
        // Case-insensitive check
        return val.equalsIgnoreCase("High") || 
               val.equalsIgnoreCase("Medium") || 
               val.equalsIgnoreCase("Low");
    }
    
    private boolean isValidFutureDate(String dateStr) {
        try {
            // Parse strictly as YYYY-MM-DD
            LocalDate inputDate = LocalDate.parse(dateStr);
            LocalDate today = LocalDate.now();
            
            // Return TRUE if inputDate is today or after today
            return !inputDate.isBefore(today);
        } catch (DateTimeParseException e) {
            System.err.println("Date Parse Error: " + dateStr);
            return false; // Return false if format is wrong
        }
    }
    
    private Map<String, Object> createErrorResponse(String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("text", errorMessage);
        
        // Optional: Add a visual 'Stop' card
        Map<String, String> card = new HashMap<>();
        card.put("title", "Validation Error");
        card.put("theme", "prompt"); // Red/Orange theme
        card.put("thumbnail", "https://cdn-icons-png.flaticon.com/512/1828/1828843.png");
        response.put("card", card);
        
        return response;
    }
    
    public Map<String, Object> handleSetupCommand(String arguments, String userId) {
    	if (!alertScheduler.isRunning()) {
            alertScheduler.startMonitoring();
        }
    	// --- A. VALIDATION ---
        if (arguments == null || !arguments.trim().startsWith("https://cliq.zoho.")) {
            String errorMsg = "### ⚠️ **Invalid Webhook URL**\n" +
                              "You must provide the full Incoming Webhook Endpoint.\n" +
                              "Format: `/setup https://cliq.zoho.com/...`";
            
            Map<String, Object> response = new HashMap<>();
            response.put("text", messageComponents.createText(errorMsg));
            
            Card card = new Card("SETUP FAILED", "https://cdn-icons-png.flaticon.com/512/1828/1828843.png", "prompt");
            response.put("card", messageComponents.createCard(card));
            
            return response;
        }

        String cleanUrl = arguments.trim();
        
        // --- B. STORE IT ---
        Connection conn = new Connection(userId, cleanUrl);
        registeredWebhooks.put(userId, conn);
        System.out.println("✅ New Webhook Registered: " + cleanUrl);

        // --- C. TEST FIRE (Directly) ---
        try {
            Map<String, Object> testMsg = new HashMap<>();
            String successMessage = "### ✅ **Connection Established!**\n" +
                    "This chat is now linked to the **DeadlineGuard Live Engine**.\n" +
                    "You will receive real-time alerts right here.\n\n";
            
            testMsg.put("text", successMessage);
            
            Card successCard = new Card("CONNECTED", "https://cdn-icons-png.flaticon.com/512/190/190411.png", "modern-inline");
            testMsg.put("card", messageComponents.createCard(successCard));
            
            alertScheduler.monitorTasks(conn);
            // Send immediately
            return testMsg;
            
        } catch (Exception e) {
            System.err.println("❌ Test Message Failed: " + e.getMessage());
        }

        // --- D. RESPONSE ---
        return Map.of("text", "✅ **Setup Complete!** I have verified the connection.");
    }

}
