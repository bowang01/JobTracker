package com.jobtrack;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jobtrack.domain.User;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.service.UserService;
import com.jobtrack.web.form.RegistrationForm;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    static final Path STORAGE_DIR;

    static {
        try {
            STORAGE_DIR = Files.createTempDirectory("jobtrack-test-files");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        // H2 in MySQL compatibility mode runs the same Flyway migration as production MySQL
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:jobtrack-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("jobtrack.storage.dir", STORAGE_DIR::toString);
        registry.add("jobtrack.admin.password", () -> "AdminPass123");
        // No demo account in tests (the tests share one database); DemoAccountTest calls DemoData directly
        registry.add("jobtrack.demo.enabled", () -> "false");
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected UserService userService;

    @Autowired
    protected UserRepository userRepository;

    /** Creates a regular user with a random email (tests share one in-memory database, so emails must not clash). */
    protected AppUserDetails newUser(String prefix) {
        RegistrationForm f = new RegistrationForm();
        f.setEmail(prefix + "_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        f.setPassword("password123");
        f.setConfirmPassword("password123");
        User u = userService.register(f);
        return new AppUserDetails(u);
    }

    protected AppUserDetails newAdmin() {
        User u = userService.createAdmin("admin_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "AdminPass123");
        return new AppUserDetails(u);
    }

    protected AppUserDetails reload(AppUserDetails me) {
        return new AppUserDetails(userRepository.findById(me.getId()).orElseThrow());
    }

    /**
     * Like a browser using a list page: POSTs form values to one of its state endpoints (filter, tab, sort,
     * page...) in the given session, checks it redirects back to the clean page URL, then GETs that page.
     * params are name/value pairs.
     */
    protected ResultActions postThenGet(AppUserDetails me, MockHttpSession session, String postUrl, String pageUrl,
                                        String... params) throws Exception {
        MockHttpServletRequestBuilder req = post(postUrl).with(user(me)).with(csrf()).session(session);
        for (int i = 0; i + 1 < params.length; i += 2) {
            req.param(params[i], params[i + 1]);
        }
        mvc.perform(req).andExpect(redirectedUrl(pageUrl));
        return mvc.perform(get(pageUrl).with(user(me)).session(session)).andExpect(status().isOk());
    }

    /** Creates an application over HTTP and returns its ID. */
    protected UUID createApplication(AppUserDetails me, String company, String position, String jdText) throws Exception {
        MvcResult r = mvc.perform(post("/applications").with(user(me)).with(csrf())
                        .param("company", company)
                        .param("positionTitle", position)
                        .param("jdText", jdText == null ? "" : jdText)
                        .param("initialStatus", "SAVED"))
                .andReturn();
        String location = r.getResponse().getRedirectedUrl();
        if (location == null || !location.matches("^/applications/[0-9a-f-]{36}$")) {
            throw new AssertionError("Creating the application failed, response: " + r.getResponse().getStatus() + " " + location);
        }
        return UUID.fromString(location.substring("/applications/".length()));
    }
}
