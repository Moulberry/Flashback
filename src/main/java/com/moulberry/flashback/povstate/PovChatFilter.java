package com.moulberry.flashback.povstate;

import java.util.Locale;
import java.util.Set;

public class PovChatFilter {

    private static final Set<String> SENSITIVE_COMMANDS = Set.of(
        "login", "l", "log", "register", "reg",
        "changepassword", "changepass", "changepw", "cp",
        "unregister", "unreg",
        "email", "mail",
        "2fa", "totp", "verify", "code",
        "logar", "entrar", "registrar", "registrarse", "mudarsenha",
        "authme", "nlogin", "loginsecurity", "jpremium", "librelogin"
    );

    public static String filterSensitiveChat(String input) {
        int searchFrom = 0;

        while (true) {
            int commandStart = input.indexOf('/', searchFrom);
            searchFrom = commandStart + 1;

            if (commandStart < 0) {
                return input;
            }

            int commandEnd = input.indexOf(' ', commandStart);
            if (commandEnd < 0) {
                return input;
            }

            int suffixStart = input.indexOf(':', commandStart);
            if (suffixStart > commandStart && suffixStart < commandEnd) {
                commandStart = suffixStart;
            }

            int remainingChars = Math.min(4, input.length() - commandEnd - 1);
            if (remainingChars <= 0) {
                return input;
            }

            String command = input.substring(commandStart+1, commandEnd).toLowerCase(Locale.ROOT);
            if (!SENSITIVE_COMMANDS.contains(command)) {
                continue;
            }

            return input.substring(0, commandEnd) + " " + "*".repeat(remainingChars);
        }
    }

}
