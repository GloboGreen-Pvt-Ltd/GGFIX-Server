package com.repairshop.saas.marketplace.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSendRequest {

    // Either body (text) or attachmentUrl (media-only message) must be present.
    // Both are allowed when a message has a caption.
    private String body;

    private String attachmentUrl;

    private String attachmentType; // IMAGE | AUDIO | FILE
}
