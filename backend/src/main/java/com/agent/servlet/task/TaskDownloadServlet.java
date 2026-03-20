package com.agent.servlet.task;

import com.agent.config.AppConfig;
import com.agent.dao.ScheduledTaskDao;
import com.agent.model.ScheduledTask;
import com.agent.util.ResponseUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * GET /api/tasks/{taskId}/download — Download the output file of a scheduled task.
 *
 * Proxies the file download from the Task Executor (Port 6000) to the frontend.
 * Supports ?token= query param for browser downloads that can't set Authorization headers.
 */
@WebServlet("/api/tasks/*/download")
public class TaskDownloadServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {
            long userId = (long) request.getAttribute("userId");

            // Extract taskId from the request URI
            String uri = request.getRequestURI();
            String contextPath = request.getContextPath();
            String relativePath = uri.substring(contextPath.length());

            // Parse: /api/tasks/{taskId}/download
            String[] parts = relativePath.split("/");
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

            // Get task details
            ScheduledTask task = ScheduledTaskDao.findByTaskId(taskId);
            if (task == null || task.getOutputFile() == null || task.getOutputFile().isEmpty()) {
                ResponseUtil.sendError(response, 404, "No output file available for this task");
                return;
            }

            // Fetch file from Task Executor
            // GET http://localhost:6000/download/{userId}/{filePath}
            String executorBaseUrl = AppConfig.TASK_EXECUTOR_WS_URL
                    .replace("ws://", "http://")
                    .replace("/ws", "");
            String downloadUrl = executorBaseUrl + "/download/" + userId + "/" + task.getOutputFile();

            URL url = new URL(downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);

            int statusCode = conn.getResponseCode();
            if (statusCode != 200) {
                ResponseUtil.sendError(response, 502, "Failed to download file from executor");
                return;
            }

            // Extract filename from output file path
            String outputFile = task.getOutputFile();
            String filename = outputFile.contains("/")
                    ? outputFile.substring(outputFile.lastIndexOf("/") + 1)
                    : outputFile;

            // Set download headers
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            // Transfer content type from executor if available
            String contentType = conn.getContentType();
            if (contentType != null) {
                response.setContentType(contentType);
            }

            int contentLength = conn.getContentLength();
            if (contentLength > 0) {
                response.setContentLength(contentLength);
            }

            // Stream file bytes to frontend
            try (InputStream in = conn.getInputStream();
                 OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            } finally {
                conn.disconnect();
            }

        } catch (Exception e) {
            ResponseUtil.sendError(response, 500, "Internal server error: " + e.getMessage());
        }
    }
}
