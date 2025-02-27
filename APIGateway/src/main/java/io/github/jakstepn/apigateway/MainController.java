package io.github.jakstepn.apigateway;

import io.github.jakstepn.common.TestClass;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

@RestController
public class MainController {

    @RequestMapping("/")
    public String home() {
        return TestClass.Test + "test";
    }

    @RequestMapping("/blog")
    public ResponseEntity<String> blog() {
        try {
            // URL of the localhost endpoint
            URI uri = new URI("http://localhost:80/");
            URL url = uri.toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set the request method to GET
            connection.setRequestMethod("GET");

            // Get the response code
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            // Read the response
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Print the response
            return new ResponseEntity<>(response.toString(), HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ResponseEntity<>("Could not send request!", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
