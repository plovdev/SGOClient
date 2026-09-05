package org.plovdev.sgoclient.example;

import org.plovdev.sgoclient.core.SGOClient;
import org.plovdev.sgoclient.core.SGOSession;
import org.plovdev.sgoclient.core.dto.SGOUserFullInfo;
import org.plovdev.sgoclient.core.dto.Schools;
import org.plovdev.sgoclient.core.http.requests.user.GetSGOUserFullInfo;
import org.plovdev.sgoclient.core.security.AuthKeys;

public class Main {
    public static void main(String[] args) {
        AuthKeys keys = AuthKeys.load("MY_NAME", "MY_PASS");

        try (SGOClient client = new SGOClient()) {
            SGOSession session = client.createSession(keys, Schools.MAOU6);

            SGOUserFullInfo info = client.execute(new GetSGOUserFullInfo());
            System.out.println(info);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}