package com.leadrat.crm.leads.api.lead;

import com.leadrat.crm.leads.api.lead.dto.LeadNoteDto;
import com.leadrat.crm.leads.api.lead.dto.LeadNoteRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/leads/{leadId}/notes")
@Tag(name = "Lead Note", description = "Activity notes recorded against a lead")
public class LeadNoteController {

    private final LeadNoteService leadNoteService;

    @Operation(summary = "Get the notes on a lead, newest first")
    @GetMapping
    public ResponseEntity<Page<LeadNoteDto>> getByLead(
            @PathVariable UUID leadId,
            @ParameterObject @PageableDefault(sort = "created", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(leadNoteService.getByLead(leadId, pageable));
    }

    @Operation(summary = "Add a note to a lead")
    @PostMapping
    public ResponseEntity<LeadNoteDto> add(@PathVariable UUID leadId,
                                           @Valid @RequestBody LeadNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leadNoteService.add(leadId, request));
    }

    @Operation(summary = "Update a note")
    @PutMapping("/{noteId}")
    public ResponseEntity<LeadNoteDto> update(@PathVariable UUID leadId,
                                              @PathVariable UUID noteId,
                                              @Valid @RequestBody LeadNoteRequest request) {
        return ResponseEntity.ok(leadNoteService.update(leadId, noteId, request));
    }

    @Operation(summary = "Soft-delete a note")
    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> delete(@PathVariable UUID leadId, @PathVariable UUID noteId) {
        leadNoteService.delete(leadId, noteId);
        return ResponseEntity.noContent().build();
    }
}
