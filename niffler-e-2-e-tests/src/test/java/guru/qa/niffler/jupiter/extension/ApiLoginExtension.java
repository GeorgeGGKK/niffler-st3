package guru.qa.niffler.jupiter.extension;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import guru.qa.niffler.api.AuthServiceClient;
import guru.qa.niffler.api.context.CookieContext;
import guru.qa.niffler.api.context.SessionStorageContext;
import guru.qa.niffler.config.Config;
import guru.qa.niffler.db.model.auth.UserEntity;
import guru.qa.niffler.jupiter.annotation.ApiLogin;
import guru.qa.niffler.jupiter.annotation.DBUser;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.Cookie;

import java.io.IOException;

import static guru.qa.niffler.jupiter.extension.DbUserExtension.NAMESPACE;
import static guru.qa.niffler.jupiter.extension.DbUserExtension.defaultPassword;

public class ApiLoginExtension implements BeforeEachCallback, AfterTestExecutionCallback {

    private final AuthServiceClient authServiceClient = new AuthServiceClient();

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        ApiLogin apiLogin = extensionContext.getRequiredTestMethod().getAnnotation(ApiLogin.class);
        String username;
        String password;
        if (apiLogin != null) {
            username = apiLogin.username();
            password = apiLogin.password();
            if(username.isEmpty() || password.isEmpty()) {
                DBUser dbUser = extensionContext.getRequiredTestMethod().getAnnotation(DBUser.class);
                if (dbUser != null) {
                    UserEntity user = extensionContext.getStore(NAMESPACE).get(extensionContext.getUniqueId(), UserEntity.class);
                    username = user.getUsername();
                    password = defaultPassword;
                }
            }
            doLogin(username, password);
        }
    }

    private void doLogin(String username, String password) {
        SessionStorageContext sessionStorageContext = SessionStorageContext.getInstance();
        sessionStorageContext.init();

        try {
            authServiceClient.doLogin(username, password);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Selenide.open(Config.getInstance().nifflerFrontUrl());
        Selenide.sessionStorage().setItem("codeChallenge", sessionStorageContext.getCodeChallenge());
        Selenide.sessionStorage().setItem("id_token", sessionStorageContext.getToken());
        Selenide.sessionStorage().setItem("codeVerifier", sessionStorageContext.getCodeVerifier());
        Cookie jsessionIdCookie = new Cookie("JSESSIONID", CookieContext.getInstance().getJSessionIdCookieValue());
        WebDriverRunner.getWebDriver().manage().addCookie(jsessionIdCookie);
    }

    @Override
    public void afterTestExecution(ExtensionContext extensionContext) throws Exception {
        SessionStorageContext.getInstance().clearContext();
        CookieContext.getInstance().clearContext();
    }
}
