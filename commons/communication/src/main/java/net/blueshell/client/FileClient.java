package net.blueshell.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "API",
        contextId = "fileClient"
)
public interface FileClient {

    @GetMapping("/download/{filename:.+}")
    ResponseEntity<Resource> downloadFile(@PathVariable("filename") String filename);

    @GetMapping("/eventPictures/{eventPictureId}")
    ResponseEntity<Resource> downloadEventPicture(@PathVariable("eventPictureId") Long eventPictureId);

    @GetMapping("/events/{eventId}/banner")
    ResponseEntity<Resource> downloadBanner(@PathVariable("eventId") Long eventId);

    @GetMapping("/memberships/{membershipId}/signature")
    ResponseEntity<Resource> downloadSignature(@PathVariable("membershipId") Long membershipId);

    @GetMapping("/users/{userId}/profilePicture")
    ResponseEntity<Resource> downloadProfilePicture(@PathVariable("userId") Long userId);
}
