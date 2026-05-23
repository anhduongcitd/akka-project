package com.example.payment.api;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Dashboard Endpoint - Serves static web UI for agent analytics.
 *
 * Endpoints:
 * - GET /dashboard - Agent analytics dashboard UI
 */
@HttpEndpoint("/dashboard")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class DashboardEndpoint {

    /**
     * Serve the agent analytics dashboard.
     */
    @Get
    public HttpResponse getDashboard() {
        try {
            InputStream is = getClass().getClassLoader()
                .getResourceAsStream("web/dashboard.html");

            if (is == null) {
                return HttpResponse.create()
                    .withStatus(404)
                    .withEntity("Dashboard not found");
            }

            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            return HttpResponse.create()
                .withStatus(200)
                .withEntity(ContentTypes.TEXT_HTML_UTF8, html);

        } catch (Exception e) {
            return HttpResponse.create()
                .withStatus(500)
                .withEntity("Error loading dashboard: " + e.getMessage());
        }
    }
}
