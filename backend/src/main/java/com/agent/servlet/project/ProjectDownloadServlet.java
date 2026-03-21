package com.agent.servlet.project;

import com.agent.config.AppConfig;
import com.agent.dao.ProjectDao;
import com.agent.model.Project;
import com.agent.util.ResponseUtil;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * GET /api/projects/{projectId}/download — Download a project as a ZIP file.
 *
 * Proxies the ZIP download from the Task Executor (Port 6000) to the frontend.
 * Supports ?token= query param for browser downloads that can't set Authorization headers.
 */
@WebServlet("/api/projects/*/download")
public class ProjectDownloadServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {
            long userId = (long) request.getAttribute("userId");

            // Extract projectId from the request URI
            String uri = request.getRequestURI();
            String contextPath = request.getContextPath();
            String relativePath = uri.substring(contextPath.length());

            // Parse: /api/projects/{projectId}/download
            String[] parts = relativePath.split("/");
            if (parts.length < 5) {
                ResponseUtil.sendError(response, 400, "Invalid path format");
                return;
            }

            String projectId = parts[3];

            // Verify project belongs to user
            if (!ProjectDao.belongsToUser(projectId, userId)) {
                ResponseUtil.sendError(response, 404, "Project not found");
                return;
            }

            // Get project details
            Project project = ProjectDao.findByProjectId(projectId);
            if (project == null) {
                ResponseUtil.sendError(response, 404, "Project not found");
                return;
            }

            // Fetch ZIP from Task Executor
            // GET http://localhost:6000/download-project/{userId}/{projectName}
            String executorBaseUrl = AppConfig.TASK_EXECUTOR_WS_URL
                    .replace("ws://", "http://")
                    .replace("/ws", "");
            String downloadUrl = executorBaseUrl + "/download-project/" + userId + "/" + project.getName();

            URL url = new URL(downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);

            int statusCode = conn.getResponseCode();
            if (statusCode != 200) {
                ResponseUtil.sendError(response, 502, "Failed to download project from executor");
                return;
            }

            // Set download headers
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + project.getName() + ".zip\"");

            int contentLength = conn.getContentLength();
            if (contentLength > 0) {
                response.setContentLength(contentLength);
            }

            // Stream ZIP bytes to frontend
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
