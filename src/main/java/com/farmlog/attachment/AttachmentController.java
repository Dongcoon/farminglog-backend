package com.farmlog.attachment;

import com.farmlog.attachment.dto.*;
import com.farmlog.common.security.UserPrincipal;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AttachmentController {
  private final AttachmentService service;

  public AttachmentController(AttachmentService service) {
    this.service = service;
  }

  @GetMapping(
      "/farms/{farmId}/{domain:work-logs|pest-control-logs|harvest-logs|sales-logs}/{recordId}/attachments")
  public List<AttachmentDto> list(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable String domain,
      @PathVariable Long recordId) {
    return service.list(p.userId(), farmId, domain, recordId);
  }

  @PutMapping(
      value =
          "/farms/{farmId}/{domain:work-logs|pest-control-logs|harvest-logs|sales-logs}/{recordId}/attachments/{clientFileId}",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public AttachmentDto upload(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable String domain,
      @PathVariable Long recordId,
      @PathVariable String clientFileId,
      @RequestPart("file") MultipartFile file) {
    return service.upload(p.userId(), farmId, domain, recordId, clientFileId, file);
  }

  @GetMapping("/farms/{farmId}/attachments/{id}/download")
  public ResponseEntity<FileSystemResource> download(
      @AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId, @PathVariable Long id) {
    AttachmentDownload f = service.download(p.userId(), farmId, id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(f.contentType()))
        .contentLength(f.fileSize())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(f.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .cacheControl(CacheControl.noStore().cachePrivate())
        .header("X-Content-Type-Options", "nosniff")
        .body(new FileSystemResource(f.path()));
  }

  @DeleteMapping("/farms/{farmId}/attachments/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable Long id,
      @RequestParam Long version) {
    service.delete(p.userId(), farmId, id, version);
  }

  @PostMapping("/farms/{farmId}/photo-only-drafts")
  @ResponseStatus(HttpStatus.CREATED)
  public PhotoDraftDtos.Response draft(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @Valid @RequestBody PhotoDraftDtos.CreateRequest req) {
    return service.createDraft(p.userId(), farmId, req);
  }

  @PutMapping(
      value = "/farms/{farmId}/photo-only-drafts/{batchId}/files/{clientFileId}",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public PhotoDraftDtos.Response draftFile(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable String batchId,
      @PathVariable String clientFileId,
      @RequestPart("file") MultipartFile file) {
    return service.uploadDraftFile(p.userId(), farmId, batchId, clientFileId, file);
  }

  @PostMapping("/farms/{farmId}/photo-only-drafts/{batchId}/commit")
  @ResponseStatus(HttpStatus.CREATED)
  public PhotoDraftDtos.CommitResponse commit(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable String batchId,
      @Valid @RequestBody PhotoDraftDtos.CommitRequest req) {
    return service.commit(p.userId(), farmId, batchId, req);
  }
}
