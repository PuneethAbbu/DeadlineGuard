package com.abbup.extension.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.abbup.extension.model.Button;
import com.abbup.extension.model.Card;
import com.abbup.extension.model.Connection;

@Service
public class AlertScheduler {

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private MessageComponents messageComponents;
    @Autowired
    private ZohoApiService zohoApiService;
    
    private ScheduledFuture<?> scheduledTask;
    private TaskScheduler taskScheduler;

    private Map<String, LocalDate> dailyAlertMemory = new HashMap<>();
    private Map<String, String> dateChangeMemory = new HashMap<>();

    public AlertScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.initialize();
        this.taskScheduler = scheduler;
    }

    // --- SCHEDULER CONTROL ---

    public boolean startMonitoring() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            System.out.println("⚠️ Monitor is already running.");
            return false;
        }
        System.out.println("🟢 STARTING ENGINE: Scheduling task for every 5 minutes...");
        
        Instant startTime = Instant.now().plusMillis(300000); // 5 minutes from now
        this.scheduledTask = taskScheduler.scheduleAtFixedRate(
            () -> this.monitorTasks(null),
            startTime,
            Duration.ofMillis(300000) 
        );
        return true;
    }

    public void stopMonitoring() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            System.out.println("🔴 ENGINE STOPPED.");
        }
    }
    
    public boolean isRunning() {
        return (scheduledTask != null && !scheduledTask.isCancelled());
    }

    // --- CORE LOGIC ---

     //If NULL, sends to ALL users (Scheduled Broadcast).
    public void monitorTasks(Connection specificUser) {
        System.out.println("🔍 Scanning 'Helios Core'...");
        List<Map<String, Object>> tasks = zohoApiService.getTasks();
        
        if (tasks == null || tasks.isEmpty()) return;

        LocalDate today = LocalDate.now();

        for (Map<String, Object> task : tasks) {
            try {
                String taskId = String.valueOf(task.get("id"));
                String taskName = (String) task.get("name");

                // CLEANUP (Only when running global broadcast)
                Map<String, Object> statusObj = (Map<String, Object>) task.get("status");
                if (!"Open".equalsIgnoreCase((String) statusObj.get("name"))) {
                    if (specificUser == null) {
                        dailyAlertMemory.remove(taskId);
                        dateChangeMemory.remove(taskId);
                    }
                    continue; 
                }

                // FEATURE A: SCHEDULE CHANGE (Broadcast Only)
                if (specificUser == null && task.containsKey("end_date") && task.get("end_date") != null) {
                    String rawDate = (String) task.get("end_date");
                    String currentDateStr = normalizeDate(rawDate);

                    if (currentDateStr != null) {
                        if (dateChangeMemory.containsKey(taskId)) {
                            String oldDateStr = dateChangeMemory.get(taskId);
                            if (!oldDateStr.equals(currentDateStr)) {
                                sendScheduleChangeAlert(taskName, oldDateStr, currentDateStr, taskId, null);
                            }
                        }
                        dateChangeMemory.put(taskId, currentDateStr);
                    }
                }

                // FEATURE B: SLA MONITOR
                // FIX: If specificUser exists, IGNORE memory check (Force Alert)
                if (specificUser == null && dailyAlertMemory.containsKey(taskId)) {
                    if (dailyAlertMemory.get(taskId).isEqual(today)) continue;
                }

                boolean isHighPriority = false;
                if (task.containsKey("priority")) {
                    Object pObj = task.get("priority");
                    String pName = (pObj instanceof Map) ? (String)((Map)pObj).get("name") : (String)pObj;
                    if ("High".equalsIgnoreCase(pName)) isHighPriority = true;
                }

                boolean shouldAlert = false;
                String timeMessage = "";

                if (task.containsKey("end_date") && task.get("end_date") != null) {
                    String rawDate = (String) task.get("end_date");
                    try {
                        LocalDate dueDate = LocalDate.parse(normalizeDate(rawDate));
                        long daysDiff = ChronoUnit.DAYS.between(today, dueDate);

                        if (daysDiff <= 1) {
                            shouldAlert = true;

                            // Auto-Escalate (Only in Broadcast mode to avoid duplicates)
                            if (specificUser == null && !isHighPriority) {
                                System.out.println("🔨 Deadline Close. Auto-Escalating...");
                                zohoApiService.updateTaskPriority(taskId, "High");
                                isHighPriority = true; 
                            }

                            if (daysDiff < 0) timeMessage = Math.abs(daysDiff) + " Days Overdue";
                            else if (daysDiff == 0) timeMessage = "Due Today";
                            else timeMessage = "Due Tomorrow"; 
                        } 
                    } catch (Exception e) { }
                }

                if (shouldAlert) {
                    String ownerName = extractOwnerName(task);
                    System.out.println("🚨 SLA Alert: " + taskName);
                    
                    //PASS TARGET (Null or Specific)
                    sendSlaAlert(taskName, ownerName, timeMessage, taskId, specificUser);
                    
                    // Update memory only if broadcasting
                    if (specificUser == null) dailyAlertMemory.put(taskId, today);
                    
                    dailyAlertMemory.put(taskId, today);
                }

            } catch (Exception e) {
                System.err.println("Skipping task: " + e.getMessage());
            }
        }
    }

    // --- SENDING LOGIC ---

    private void sendSlaAlert(String taskName, String owner, String timeMessage, String taskId, Connection target) {
        Map<String, Object> messageCard = new HashMap<>();
        String msg = "⚠️ **SLA BREACH:** Task '" + taskName + "' is **" + timeMessage + "**.\n" +
                     "👤 **Owner:** " + owner;
        messageCard.put("text", messageComponents.createText(msg));
        
        String iconUrl = "https://cdn-icons-png.flaticon.com/512/595/595067.png"; 
        Card card = new Card("CRITICAL ALERT", iconUrl, "modern-inline");
        messageCard.put("card", messageComponents.createCard(card));

        //ROUTER
        sendOrBroadcast(messageCard, target);
    }

    private void sendScheduleChangeAlert(String taskName, String oldDate, String newDate, String taskId, Connection target) {
        LocalDate oldD = LocalDate.parse(oldDate);
        LocalDate newD = LocalDate.parse(newDate);
        
        String changeType = newD.isBefore(oldD) ? "⚠️ **PREPONED**" : "🗓️ **POSTPONED**";
        String theme = newD.isBefore(oldD) ? "prompt" : "modern-inline";
        String icon = "https://cdn-icons-png.flaticon.com/512/2693/2693554.png";

        Map<String, Object> messageCard = new HashMap<>();
        String msg = changeType + ": Task '" + taskName + "'\n" +
                     "🔹 **Was:** " + oldDate + "\n" +
                     "🔹 **Now:** " + newDate;

        messageCard.put("text", messageComponents.createText(msg));
        Card card = new Card("SCHEDULE UPDATE", icon, theme);
        messageCard.put("card", messageComponents.createCard(card));
        

        //ROUTER
        sendOrBroadcast(messageCard, target);
    }

    //ROUTER: Decides whether to send to ONE person or ALL
    private void sendOrBroadcast(Map<String, Object> payload, Connection target) {
        // A. SINGLE USER (Initial Scan)
        if (target != null) {
            try {
                restTemplate.postForObject(target.getWebhookUrl(), payload, String.class);
                System.out.println("✅ Direct Alert sent to: " + target.getUserId());
            } catch (Exception e) {
                System.err.println("❌ Failed to send to user: " + e.getMessage());
            }
        } 
        // B. BROADCAST (Scheduler)
        else {
            if (BotService.registeredWebhooks.isEmpty()) return;

            for (Connection user : BotService.registeredWebhooks.values()) {
                try {
                    restTemplate.postForObject(user.getWebhookUrl(), payload, String.class);
                    System.out.println("✅ Broadcast sent to: " + user.getUserId());
                } catch (Exception e) {
                    System.err.println("❌ Failed to send to " + user.getUserId());
                }
            }
        }
    }

    // --- HELPERS (Unchanged) ---
    private String normalizeDate(String rawDate) {
        try {
            if (rawDate.contains("T")) {
                return ZonedDateTime.parse(rawDate).toLocalDate().toString();
            } else {
                DateTimeFormatter fallback = DateTimeFormatter.ofPattern("MM-dd-yyyy");
                return LocalDate.parse(rawDate, fallback).toString();
            }
        } catch (Exception e) { return null; }
    }

    private String extractOwnerName(Map<String, Object> task) {
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
                                return (String) ((Map<?, ?>) firstOwner).get("name");
                            }
                        }
                    }
                }
            }
        }
        return "Unassigned";
    }
}