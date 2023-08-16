package dev.unnm3d.redischat.mail;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.SuggestionInfo;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.unnm3d.redischat.Permission;
import lombok.AllArgsConstructor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@AllArgsConstructor
public class MailCommand {

    private MailManager mailManager;


    public CommandAPICommand getCommand() {
        return new CommandAPICommand("rmail")
                .withPermission(Permission.REDIS_CHAT_MAIL_READ.getPermission())
                .withAliases("mail", "mails")
                .withSubcommand(getSendSubCommand())
                .withSubcommand(getWebUISubCommand())
                .executesPlayer((sender, args) -> {
                    mailManager.getMailGUI().openPublicMailGui(sender);
                });

    }

    public CommandAPICommand getSendSubCommand() {
        return new CommandAPICommand("send")
                .withPermission(Permission.REDIS_CHAT_MAIL_WRITE.getPermission())
                .withArguments(
                        new StringArgument("player")
                                .replaceSuggestions(ArgumentSuggestions.stringsAsync(getPlayerRecipients())),
                        new GreedyStringArgument("title")
                                .replaceSuggestions(ArgumentSuggestions.strings("<aqua>Mail Object/Title</aqua>")))
                .executesPlayer((sender, args) -> {
                    String recipient = (String) args.get(0);
                    assert recipient != null;
                    if (recipient.equals("#Public") && !sender.hasPermission(Permission.REDIS_CHAT_MAIL_WRITE_PUBLIC.getPermission())) {
                        mailManager.getPlugin().getComponentProvider().sendMessage(sender, mailManager.getPlugin().messages.noPermission);
                        return;
                    }

                    mailManager.getPlugin().getWebEditorAPI().startSession("Mail Content", "/rmail webui {token}", "RedisMail")
                            .thenAccept(session -> mailManager.startEditorMode(
                                    sender,
                                    recipient,
                                    (String) args.get(1),
                                    session));
                });
    }

    public CommandAPICommand getWebUISubCommand() {
        return new CommandAPICommand("webui")
                .withArguments(new StringArgument("token"))
                .withOptionalArguments(new MultiLiteralArgument("action", List.of("confirm", "abort", "preview")))
                .executes((sender, args) -> {
                    String token = (String) args.get(0);
                    Optional<Object> action = args.getOptional(1);

                    if (token == null) {
                        mailManager.getPlugin().messages.sendMessage(sender, mailManager.getPlugin().messages.missing_arguments);
                        return;
                    }

                    if (action.isEmpty()) {
                        mailManager.getPlugin().getWebEditorAPI().retrieveSession(token)
                                .thenAccept(mailContent -> {
                                            mailManager.stopEditorMode((Player) sender, mailContent);
                                            mailManager.getPlugin().getComponentProvider().sendMessage(sender,
                                                    mailManager.getPlugin().messages.mailEditorConfirm
                                                            .replace("%token%", token)
                                            );
                                        }
                                );
                        return;
                    }

                    switch ((String) action.get()) {
                        case "confirm" -> mailManager.confirmSendMail((Player) sender, true);
                        case "abort" -> mailManager.confirmSendMail((Player) sender, false);
                        case "preview" -> mailManager.previewMail((Player) sender);
                    }

                });
    }

    public Function<SuggestionInfo<CommandSender>, CompletableFuture<String[]>> getPlayerRecipients() {
        return commandSenderSuggestionInfo ->
                CompletableFuture.supplyAsync(() -> {
                    List<String> list = new ArrayList<>(
                            mailManager.getPlugin().getPlayerListManager().getPlayerList().stream()
                                    .filter(s -> s.toLowerCase().startsWith(commandSenderSuggestionInfo.currentArg()))
                                    .toList()
                    );
                    if (commandSenderSuggestionInfo.sender().hasPermission(Permission.REDIS_CHAT_MAIL_WRITE_PUBLIC.getPermission()))
                        list.add("#Public");
                    return list.toArray(new String[0]);
                });
    }

}
