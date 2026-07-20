package com.farmlog.export;

import com.farmlog.common.security.UserPrincipal;
import com.farmlog.export.dto.*;
import com.farmlog.records.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/farms/{farmId}/exports")
public class ExportController {
    private final ExportService service;
    public ExportController(ExportService service){this.service=service;}

    @PostMapping
    public ResponseEntity<ExportJobResponse> create(@AuthenticationPrincipal UserPrincipal principal,@PathVariable Long farmId,
                                                     @Valid @RequestBody ExportCreateRequest request){
        ExportJobResponse body=service.create(principal.userId(),farmId,request);
        return ResponseEntity.accepted().location(URI.create("/farms/"+farmId+"/exports/"+body.id())).body(body);
    }
    @GetMapping public PageResponse<ExportJobResponse> list(@AuthenticationPrincipal UserPrincipal principal,@PathVariable Long farmId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(principal.userId(),farmId,page,size);}
    @GetMapping("/{id}") public ExportJobResponse detail(@AuthenticationPrincipal UserPrincipal principal,@PathVariable Long farmId,@PathVariable Long id){return service.detail(principal.userId(),farmId,id);}
    @GetMapping("/{id}/download") public ResponseEntity<FileSystemResource> download(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long farmId,@PathVariable Long id){
        ExportDownload file=service.download(principal.userId(),farmId,id);
        ContentDisposition disposition=ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).contentLength(file.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString())
                .cacheControl(CacheControl.noStore().cachePrivate()).body(new FileSystemResource(file.path()));
    }
}
