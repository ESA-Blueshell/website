package net.blueshell.common.client;

import net.blueshell.common.dto.EventSignUpDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Stream;

@FeignClient(name = "API")
public interface EventSignUpClient {

    @GetMapping("/events/signups")
    List<EventSignUpDTO> getMySignUps();

    @GetMapping("/events/signups/byAccessToken/{accessToken}")
    EventSignUpDTO getSignUpByAccessToken(@PathVariable("accessToken") String accessToken);

    @GetMapping("/events/{id}/signups")
    Stream<EventSignUpDTO> getAllSignUps(@PathVariable("id") Long eventId);

    @PostMapping("/events/{id}/signups")
    EventSignUpDTO createSignUp(@PathVariable("id") Long eventId,
                                @RequestBody EventSignUpDTO dto) throws Exception;

    @PutMapping("/events/{eventId}/signups")
    EventSignUpDTO updateSignUp(@PathVariable("eventId") Long eventId,
                                @RequestBody EventSignUpDTO dto,
                                @RequestParam(value = "accessToken", required = false) String accessToken);

    @DeleteMapping("/events/signups/{eventSignupId}")
    void deleteSignup(@PathVariable("eventSignupId") Long eventSignupId,
                      @RequestParam(value = "accessToken", required = false) String accessToken);
}
