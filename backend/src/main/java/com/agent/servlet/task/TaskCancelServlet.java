package com.agent.servlet.task;

import com.agent.dao.ScheduledTaskDao;
import com.agent.service.TaskExecutorClient;
import com.agent.util.ResponseUtil;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * POST /api/tasks/{taskId}/cancel — Cancel a running scheduled task.
 *
 * Path format: /api/tasks/{taskId}/cancel
 */
@WebServlet("/api/tasks/*/cancel")
public class TaskCancelServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {
            long userId = (long) request.getAttribute("userId");

            // Extract taskId from the request URI
            // URI format: /api/tasks/{taskId}/cancel
            String uri = request.getRequestURI();
            String contextPath = request.getContextPath();
            String relativePath = uri.substring(contextPath.length());

            // Parse: /api/tasks/{taskId}/cancel
            String[] parts = relativePath.split("/");
            // parts = ["", "api", "tasks", "{taskId}", "cancel"]

            if (parts.length < 5) {
                ResponseUtil.sendError(response, 400, "Invalid path format");
                return;
            }

            String taskId = parts[3];

            // Verify task belongs to user
            if (!ScheduledTaskDao.belongsToUser(taskId, userId)) {
                ResponseUtil.sendError(response, 404, "Task not found");
                return;
            }

            // Send cancel command via WebSocket to Task Executor
            try {
                TaskExecutorClient client = TaskExecutorClient.getInstance();
                if (client.isConnected()) {
                    JsonObject cancelCommand = new JsonObject();
                    cancelCommand.addProperty("type", "cancel_task");
                    cancelCommand.addProperty("task_id", taskId);
                    client.send(cancelCommand);
                }
            } catch (Exception e) {
                System.err.println("Failed to send cancel to Task Executor: " + e.getMessage());
                // Continue — still update DB status even if executor is unreachable
            }

            // Update status in DB
            ScheduledTaskDao.updateStatus(taskId, "cancelled");

            // Build response
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("task_id", taskId);
            data.put("status", "cancelled");

            ResponseUtil.sendSuccess(response, data);

        } catch (Exception e) {
            ResponseUtil.sendError(response, 500, "Internal server error: " + e.getMessage());
        }
    }
}
