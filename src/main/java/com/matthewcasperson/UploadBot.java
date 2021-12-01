// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.matthewcasperson;

import com.codepoetics.protonpack.collectors.CompletableFutures;
import com.microsoft.bot.builder.ActivityHandler;
import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.builder.teams.TeamsActivityHandler;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.Attachment;
import com.microsoft.bot.schema.ChannelAccount;
import com.microsoft.bot.schema.ResultPair;
import com.microsoft.bot.schema.Serialization;
import com.microsoft.bot.schema.teams.FileDownloadInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * This class implements the functionality of the Bot.
 *
 * <p>
 * This is where application specific logic for interacting with the users would be added. For this
 * sample, the {@link #onMessageActivity(TurnContext)} echos the text back to the user. The {@link
 * #onMembersAdded(List, TurnContext)} will send a greeting to new conversation participants.
 * </p>
 */
public class UploadBot extends ActivityHandler {

  @Override
  protected CompletableFuture<Void> onMembersAdded(
      List<ChannelAccount> membersAdded,
      TurnContext turnContext
  ) {
    return membersAdded.stream()
        .filter(
            member -> !StringUtils
                .equals(member.getId(), turnContext.getActivity().getRecipient().getId())
        ).map(channel -> turnContext.sendActivity(
            MessageFactory.text("Welcome! Post a message with an attachment and I'll download it!")))
        .collect(CompletableFutures.toFutureList()).thenApply(resourceResponses -> null);
  }

  @Override
  protected CompletableFuture<Void> onMessageActivity(TurnContext turnContext) {
    if (messageWithDownload(turnContext.getActivity())) {
      final Attachment attachment = turnContext.getActivity().getAttachments().get(0);
      return downloadAttachment(attachment)
          .thenCompose(result -> !result.result()
              ? turnContext.sendActivityBlind(
              MessageFactory.text("Failed to download the attachment"))
              : turnContext.sendActivityBlind(
                  MessageFactory.text(
                      "Downloaded file " + attachment.getName() + ". It was "
                          + getFileSize(result.getRight())
                          + " bytes long and appears to be of type "
                          + getFileType(result.getRight())))
          );
    }

    return turnContext.sendActivity(
        MessageFactory.text("Post a message with an attachment and I'll download it!")
    ).thenApply(sendResult -> null);
  }

  private boolean messageWithDownload(Activity activity) {
    return activity.getAttachments() != null
        && activity.getAttachments().size() > 0
        && StringUtils.equalsIgnoreCase(
        activity.getAttachments().get(0).getContentType(),
        FileDownloadInfo.CONTENT_TYPE);
  }

  private CompletableFuture<ResultPair<String>> downloadAttachment(final Attachment attachment) {
    final AtomicReference<ResultPair<String>> result = new AtomicReference<>();

    return CompletableFuture.runAsync(() -> {
          try {
            final FileDownloadInfo fileDownload = Serialization
                .getAs(attachment.getContent(), FileDownloadInfo.class);
            final File filePath = Files.createTempFile(
                FilenameUtils.getBaseName(attachment.getName()),
                "." + FilenameUtils.getExtension(attachment.getName())).toFile();

            FileUtils.copyURLToFile(
                new URL(fileDownload.getDownloadUrl()),
                filePath,
                30000,
                30000);

            result.set(new ResultPair<>(true, filePath.getAbsolutePath()));
          } catch (Throwable t) {
            result.set(new ResultPair<>(false, t.getLocalizedMessage()));
          }
        })
        .thenApply(aVoid -> result.get());
  }

  private long getFileSize(final String path) {
    try {
      return Files.size(Paths.get(path));
    } catch (IOException e) {
      return -1;
    }
  }

  private String getFileType(final String path) {
    try {
      final String type = Files.probeContentType(Paths.get(path));
      return type == null ? "unknown" : type;
    } catch (IOException e) {
      return "unknown";
    }
  }
}
