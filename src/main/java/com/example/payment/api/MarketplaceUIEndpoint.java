package com.example.payment.api;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Marketplace UI Endpoint - Serves marketplace web UI.
 */
@HttpEndpoint("/marketplace-ui")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class MarketplaceUIEndpoint {

    @Get
    public HttpResponse serveMarketplacePage() {
        try {
            var htmlPath = Paths.get("src/main/resources/web/marketplace.html");
            var content = Files.readString(htmlPath);
            return HttpResponse.create()
                .withEntity(HttpEntities.create(ContentTypes.TEXT_HTML_UTF8, content));
        } catch (Exception e) {
            return HttpResponse.create()
                .withStatus(500)
                .withEntity("Error loading marketplace page: " + e.getMessage());
        }
    }
}
