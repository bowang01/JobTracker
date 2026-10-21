package com.jobtrack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.jobtrack.domain.InterviewQuestion;
import com.jobtrack.domain.ProgrammingLanguage;
import com.jobtrack.domain.QuestionCategory;
import com.jobtrack.domain.Visibility;
import com.jobtrack.repository.InterviewQuestionRepository;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.service.PublicQuestion;

/** Interview question bank and interview hall. */
class QuestionBankTest extends IntegrationTestBase {

    @Autowired
    InterviewQuestionRepository questions;

    /** Creates a question over HTTP and returns it. */
    private InterviewQuestion addQuestion(AppUserDetails me, String category, String text, String answer,
                                          String visibility) throws Exception {
        var req = post("/questions").with(user(me)).with(csrf())
                .param("category", category)
                .param("questionText", text)
                .param("answerText", answer);
        if (visibility != null) {
            req.param("visibility", visibility);
        }
        mvc.perform(req).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("success"));
        return questions.searchOwn(me.getId(), null, null, null, null).stream()
                .filter(q -> q.getQuestionText().equals(text)).findFirst().orElseThrow();
    }

    /**
     * Opens a list page the way a browser does now that filters are not in the URL: the "query" (written like
     * "?category=TECHNICAL&q=foo" for readability) is turned into POSTs to the tab and filter endpoints in a
     * fresh session, then the plain page is loaded.
     */
    private MvcResult openList(AppUserDetails me, String base, String query) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String category = null;
        List<String> filter = new java.util.ArrayList<>();
        if (!query.isEmpty()) {
            for (String kv : query.substring(1).split("&")) {
                String[] p = kv.split("=", 2);
                if (p[0].equals("category")) {
                    category = p[1];
                } else {
                    filter.add(p[0]);
                    filter.add(p[1]);
                }
            }
        }
        if (category != null) {
            postThenGet(me, session, base + "/tab", base, "category", category);
        }
        if (!filter.isEmpty()) {
            postThenGet(me, session, base + "/filter", base, filter.toArray(new String[0]));
        }
        return mvc.perform(get(base).with(user(me)).session(session)).andExpect(status().isOk()).andReturn();
    }

    @SuppressWarnings("unchecked")
    private List<InterviewQuestion> myList(AppUserDetails me, String query) throws Exception {
        return (List<InterviewQuestion>) openList(me, "/questions", query).getModelAndView().getModel().get("questions");
    }

    @SuppressWarnings("unchecked")
    private List<PublicQuestion> hall(AppUserDetails me, String query) throws Exception {
        return ((Page<PublicQuestion>) openList(me, "/hall", query).getModelAndView().getModel().get("page")).getContent();
    }

    private static String unique(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void usersRecordTechnicalAndBehavioralQuestions_privateByDefault() throws Exception {
        AppUserDetails me = newUser("qbank");
        InterviewQuestion tech = addQuestion(me, "TECHNICAL", unique("What is a hash map?"), "Buckets + hashing", null);
        InterviewQuestion beh = addQuestion(me, "BEHAVIORAL", unique("Tell me about a conflict"), "STAR story", null);

        assertThat(tech.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(tech.getPublishedAt()).isNull();
        assertThat(beh.getCategory()).isEqualTo(QuestionCategory.BEHAVIORAL);

        assertThat(myList(me, "")).extracting(InterviewQuestion::getId).containsExactlyInAnyOrder(tech.getId(), beh.getId());
        assertThat(myList(me, "?category=TECHNICAL")).extracting(InterviewQuestion::getId).containsExactly(tech.getId());
        assertThat(myList(me, "?category=behavioral")).extracting(InterviewQuestion::getId).containsExactly(beh.getId());
        assertThat(myList(me, "?q=buckets")).extracting(InterviewQuestion::getId).containsExactly(tech.getId());
        assertThat(myList(me, "?category=nonsense")).hasSize(2); // invalid filter is ignored

        // Edit and delete
        mvc.perform(post("/questions/{id}", tech.getId()).with(user(me)).with(csrf())
                .param("category", "TECHNICAL").param("questionText", "Edited question")
                .param("answerText", "Edited answer").param("visibility", "PRIVATE").param("selfRating", "POOR"))
                .andExpect(status().is3xxRedirection());
        InterviewQuestion edited = questions.findById(tech.getId()).orElseThrow();
        assertThat(edited.getQuestionText()).isEqualTo("Edited question");
        assertThat(edited.getSelfRating().name()).isEqualTo("POOR");
        assertThat(myList(me, "?rating=POOR")).extracting(InterviewQuestion::getId).containsExactly(tech.getId());

        mvc.perform(post("/questions/{id}/delete", beh.getId()).with(user(me)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(questions.findById(beh.getId())).isEmpty();

        // Validation
        mvc.perform(post("/questions").with(user(me)).with(csrf()).param("category", "TECHNICAL").param("questionText", " "))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "questionText"));
    }

    @Test
    void privateQuestionsAreInvisibleToOthers() throws Exception {
        AppUserDetails alice = newUser("alice");
        AppUserDetails bob = newUser("bob");
        InterviewQuestion secret = addQuestion(alice, "TECHNICAL", unique("Alice private question"), "alice answer", null);

        mvc.perform(get("/questions/{id}/edit", secret.getId()).with(user(bob))).andExpect(status().isNotFound());
        mvc.perform(post("/questions/{id}", secret.getId()).with(user(bob)).with(csrf())
                .param("category", "TECHNICAL").param("questionText", "hacked").param("visibility", "PUBLIC"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/questions/{id}/delete", secret.getId()).with(user(bob)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/hall/{id}/copy", secret.getId()).with(user(bob)).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(myList(bob, "")).extracting(InterviewQuestion::getId).doesNotContain(secret.getId());
        assertThat(hall(bob, "")).extracting(PublicQuestion::id).doesNotContain(secret.getId());
        assertThat(questions.findById(secret.getId()).orElseThrow().getQuestionText()).startsWith("Alice private question");
    }

    @Test
    void publicQuestionsAppearInTheHall_butAnswersNeverDo() throws Exception {
        AppUserDetails alice = newUser("alice");
        AppUserDetails bob = newUser("bob");
        String text = unique("Design a URL shortener");
        String answer = "SECRET-ANSWER-" + UUID.randomUUID();
        InterviewQuestion shared = addQuestion(alice, "TECHNICAL", text, answer, "PUBLIC");
        assertThat(shared.getPublishedAt()).isNotNull();

        assertThat(hall(bob, "")).extracting(PublicQuestion::id).contains(shared.getId());
        assertThat(hall(bob, "?category=BEHAVIORAL")).extracting(PublicQuestion::id).doesNotContain(shared.getId());

        // The answer is not on the page and searching for it finds nothing
        mvc.perform(get("/hall").with(user(bob)))
                .andExpect(content().string(containsString(text)))
                .andExpect(content().string(not(containsString(answer))));
        assertThat(hall(bob, "?q=" + answer)).isEmpty();
        assertThat(hall(bob, "?q=url shortener")).extracting(PublicQuestion::id).contains(shared.getId());

        // Bob copies it into his own bank: a new private question without the answer
        mvc.perform(post("/hall/{id}/copy", shared.getId()).with(user(bob)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        InterviewQuestion copy = myList(bob, "").stream()
                .filter(q -> q.getQuestionText().equals(text)).findFirst().orElseThrow();
        assertThat(copy.getId()).isNotEqualTo(shared.getId());
        assertThat(copy.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(copy.getAnswerText()).isNull();

        // Alice cannot copy her own question
        mvc.perform(post("/hall/{id}/copy", shared.getId()).with(user(alice)).with(csrf()))
                .andExpect(flash().attributeExists("error"));

        // Making it private again removes it from the hall
        mvc.perform(post("/questions/{id}", shared.getId()).with(user(alice)).with(csrf())
                .param("category", "TECHNICAL").param("questionText", text).param("answerText", answer)
                .param("visibility", "PRIVATE"))
                .andExpect(status().is3xxRedirection());
        assertThat(questions.findById(shared.getId()).orElseThrow().getPublishedAt()).isNull();
        assertThat(hall(bob, "")).extracting(PublicQuestion::id).doesNotContain(shared.getId());
    }

    @Test
    void adminCanBrowseAndModerateTheHall_butHasNoQuestionBank() throws Exception {
        AppUserDetails admin = newAdmin();
        AppUserDetails alice = newUser("alice");
        AppUserDetails bob = newUser("bob");
        InterviewQuestion shared = addQuestion(alice, "BEHAVIORAL", unique("Why do you want to work here?"), "mine", "PUBLIC");

        InterviewQuestion privateOne = addQuestion(alice, "TECHNICAL", unique("Alice private"), "ADMIN-HIDDEN-ANSWER-7f3k", "PRIVATE");
        List<PublicQuestion> adminHall = hall(admin, "");
        assertThat(adminHall).extracting(PublicQuestion::id).contains(shared.getId(), privateOne.getId());
        assertThat(adminHall).filteredOn(p -> p.id().equals(privateOne.getId())).singleElement()
                .satisfies(p -> assertThat(p.ownerEmail()).isEqualTo(alice.getUsername()));
        assertThat(hall(admin, "?visibility=PRIVATE")).extracting(PublicQuestion::id)
                .contains(privateOne.getId()).doesNotContain(shared.getId());
        mvc.perform(get("/hall").with(user(admin))).andExpect(content().string(not(containsString("ADMIN-HIDDEN-ANSWER-7f3k"))));
        // Regular users still only see public questions, anonymously (visibility filter is ignored for them)
        assertThat(hall(bob, "?visibility=PRIVATE")).extracting(PublicQuestion::id).doesNotContain(privateOne.getId());
        assertThat(hall(bob, "")).allSatisfy(p -> assertThat(p.ownerEmail()).isNull());
        mvc.perform(get("/questions").with(user(admin))).andExpect(status().isForbidden());
        mvc.perform(post("/questions").with(user(admin)).with(csrf())
                .param("category", "TECHNICAL").param("questionText", "x")).andExpect(status().isForbidden());
        mvc.perform(post("/hall/{id}/copy", shared.getId()).with(user(admin)).with(csrf())).andExpect(status().isForbidden());

        // Regular users cannot moderate
        mvc.perform(post("/hall/{id}/unpublish", shared.getId()).with(user(bob)).with(csrf()))
                .andExpect(status().isForbidden());

        // Admin takes it out of the hall: it becomes private but is not deleted
        mvc.perform(post("/hall/{id}/unpublish", shared.getId()).with(user(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        InterviewQuestion after = questions.findById(shared.getId()).orElseThrow();
        assertThat(after.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(after.getAnswerText()).isEqualTo("mine");
        assertThat(hall(bob, "")).extracting(PublicQuestion::id).doesNotContain(shared.getId());
    }

    @Test
    void deletingAUserDeletesTheirQuestions() throws Exception {
        AppUserDetails admin = newAdmin();
        AppUserDetails gone = newUser("gone");
        InterviewQuestion q = addQuestion(gone, "TECHNICAL", unique("Soon deleted"), "a", "PUBLIC");

        mvc.perform(post("/admin/users/{id}/delete", gone.getId()).with(user(admin)).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        assertThat(questions.findById(q.getId())).isEmpty();
    }

    @Test
    void adminSeesAllQuestions_publicAndPrivate_butNeverAnswers() throws Exception {
        AppUserDetails admin = newAdmin();
        AppUserDetails alice = newUser("alice");
        String privateText = unique("Private question");
        String publicText = unique("Public question");
        String answer = "SECRET-ANSWER-" + UUID.randomUUID();
        InterviewQuestion priv = addQuestion(alice, "TECHNICAL", privateText, answer, "PRIVATE");
        InterviewQuestion pub = addQuestion(alice, "BEHAVIORAL", publicText, answer, "PUBLIC");

        MvcResult r = postThenGet(admin, new MockHttpSession(), "/admin/questions/filter", "/admin/questions",
                        "ownerId", alice.getId().toString())
                .andExpect(content().string(containsString(privateText)))
                .andExpect(content().string(containsString(publicText)))
                .andExpect(content().string(not(containsString(answer))))
                .andReturn();
        @SuppressWarnings("unchecked")
        Page<com.jobtrack.service.AdminQuestion> page =
                (Page<com.jobtrack.service.AdminQuestion>) r.getModelAndView().getModel().get("page");
        assertThat(page.getContent()).extracting(com.jobtrack.service.AdminQuestion::id)
                .containsExactlyInAnyOrder(priv.getId(), pub.getId());

        // Filters
        postThenGet(admin, new MockHttpSession(), "/admin/questions/filter", "/admin/questions",
                        "ownerId", alice.getId().toString(), "visibility", "PUBLIC")
                .andExpect(content().string(containsString(publicText)))
                .andExpect(content().string(not(containsString(privateText))));
        // Searching for the answer finds nothing
        postThenGet(admin, new MockHttpSession(), "/admin/questions/filter", "/admin/questions", "q", answer)
                .andExpect(content().string(not(containsString(privateText))));

        // Regular users cannot see this page
        mvc.perform(get("/admin/questions").with(user(alice))).andExpect(status().isForbidden());
    }

    @Test
    void visitorsCanBrowseTheHallWithoutSigningIn_butCannotChangeAnything() throws Exception {
        AppUserDetails alice = newUser("visible");
        String publicText = unique("Explain optimistic locking");
        String privateText = unique("A private draft question");
        String answer = "VISITOR-HIDDEN-ANSWER-" + UUID.randomUUID();
        InterviewQuestion shared = addQuestion(alice, "TECHNICAL", publicText, answer, "PUBLIC");
        addQuestion(alice, "BEHAVIORAL", privateText, "", "PRIVATE");

        // No user: public questions only, never answers, never the author
        mvc.perform(get("/hall"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(publicText)))
                .andExpect(content().string(not(containsString(privateText))))
                .andExpect(content().string(not(containsString(answer))))
                .andExpect(content().string(not(containsString(alice.getUsername()))));

        // Tabs, search and paging work for visitors too (state in the visitor's session)
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/hall/filter").session(session).with(csrf()).param("q", "optimistic locking"))
                .andExpect(redirectedUrl("/hall"));
        mvc.perform(get("/hall").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(publicText)));

        // Saving a question or moderating needs an account: visitors are sent to the sign-in page
        mvc.perform(post("/hall/{id}/copy", shared.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(post("/hall/{id}/unpublish", shared.getId()).with(csrf()))
                .andExpect(redirectedUrlPattern("**/login"));
        assertThat(questions.findById(shared.getId()).orElseThrow().getVisibility()).isEqualTo(Visibility.PUBLIC);

        // The rest of the site still requires signing in
        mvc.perform(get("/questions")).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/applications")).andExpect(redirectedUrlPattern("**/login"));
    }

    // ------------------------------------------------------------------ Programming language of technical questions

    /** Creates a public question with a language over HTTP and returns it. */
    private InterviewQuestion addWithLanguage(AppUserDetails me, String category, String language, String text) throws Exception {
        mvc.perform(post("/questions").with(user(me)).with(csrf())
                        .param("category", category)
                        .param("language", language)
                        .param("questionText", text)
                        .param("visibility", "PUBLIC"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));
        return questions.searchOwn(me.getId(), null, null, null, null).stream()
                .filter(q -> q.getQuestionText().equals(text)).findFirst().orElseThrow();
    }

    private static List<String> texts(List<?> items) {
        return items.stream().map(i -> i instanceof InterviewQuestion q ? q.getQuestionText()
                : ((PublicQuestion) i).questionText()).toList();
    }

    @Test
    void technicalQuestionsCanBeTaggedAndFilteredByLanguage() throws Exception {
        AppUserDetails me = newUser("lang");
        String javaText = unique("Explain the Java memory model");
        String pythonText = unique("Explain the Python GIL");
        String behavioralText = unique("Tell me about a tight deadline");
        InterviewQuestion java = addWithLanguage(me, "TECHNICAL", "JAVA", javaText);
        InterviewQuestion python = addWithLanguage(me, "TECHNICAL", "PYTHON", pythonText);
        // Behavioral questions never keep a language, even if one is posted
        InterviewQuestion behavioral = addWithLanguage(me, "BEHAVIORAL", "JAVA", behavioralText);
        assertThat(java.getLanguage()).isEqualTo(ProgrammingLanguage.JAVA);
        assertThat(python.getLanguage()).isEqualTo(ProgrammingLanguage.PYTHON);
        assertThat(behavioral.getLanguage()).isNull();

        // My questions: the language filter keeps only technical questions about that language
        assertThat(texts(myList(me, "?language=JAVA"))).contains(javaText).doesNotContain(pythonText, behavioralText);
        assertThat(texts(myList(me, "?category=TECHNICAL&language=PYTHON"))).contains(pythonText).doesNotContain(javaText);
        // ...and is ignored on the Behavioral tab, which has no languages
        assertThat(texts(myList(me, "?category=BEHAVIORAL&language=JAVA"))).contains(behavioralText);

        // Interview hall: same filter for everyone else
        AppUserDetails other = newUser("lang_other");
        assertThat(texts(hall(other, "?language=PYTHON"))).contains(pythonText).doesNotContain(javaText, behavioralText);
        assertThat(texts(hall(other, "?language=JAVA"))).contains(javaText).doesNotContain(pythonText);

        // Copying a question from the hall keeps its language
        mvc.perform(post("/hall/{id}/copy", python.getId()).with(user(other)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        InterviewQuestion copy = questions.searchOwn(other.getId(), null, null, null, null).stream()
                .filter(q -> q.getQuestionText().equals(pythonText)).findFirst().orElseThrow();
        assertThat(copy.getLanguage()).isEqualTo(ProgrammingLanguage.PYTHON);

        // Admin questions page filters by language too
        AppUserDetails admin = newAdmin();
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/admin/questions/filter").with(user(admin)).with(csrf()).session(session)
                        .param("language", "JAVA").param("q", javaText.substring(javaText.length() - 8)))
                .andExpect(redirectedUrl("/admin/questions"));
        mvc.perform(get("/admin/questions").with(user(admin)).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(javaText)))
                .andExpect(content().string(not(containsString(pythonText))));
    }
}
