package uk.gov.di.accountmanagement.testsupport.helpers;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.HashMap;
import java.util.Map;

public class Injector {
    private final RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> handler;
    private final String endpoint;

    private final String pathDescription;

    private final Map<Integer, String> pathParams;

    private final Map<String, Object> authorizer;


    public Injector(RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> handler, String endpoint, String pathDescription){
        this.endpoint = endpoint;
        this.handler = handler;
        this.pathDescription = pathDescription;
        this.pathParams = new HashMap<>();
        this.findPathParams();
        this.authorizer = new HashMap<>();
    }

    public Injector(RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> handler, String endpoint, String pathDescription, Map<String, Object> authorizer){
        this.endpoint = endpoint;
        this.handler = handler;
        this.pathDescription = pathDescription;
        this.pathParams = new HashMap<>();
        this.findPathParams();
        this.authorizer = authorizer;
    }

    public RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> getHandler() {
        return handler;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Map<Integer, String> getPathParams(){
        return this.pathParams;
    }

    public Map<String, Object> getAuthorizer() { return this.authorizer; }

    private void findPathParams(){
        String[] arr = pathDescription.split("/");
        for(int i = 0 ; i < arr.length; i++){
            if (arr[i].charAt(0) == '{'){
                pathParams.put(i,arr[i].substring(1, arr.length));
                System.out.println("added path param : " + pathParams.get(i) + " with key: " + i);
            }
        }
    }

}
