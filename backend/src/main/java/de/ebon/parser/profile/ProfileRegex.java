package de.ebon.parser.profile;

import static de.ebon.parser.profile.ProfileValidationError.Code.*;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared v1 execution boundary for the validator and interpreter. RE2/J uses an NFA, not
 * backtracking. Input, compiled program and total work are bounded before matching; no
 * timeout thread, cancellation or unbounded java.util.regex execution is involved.
 */
public final class ProfileRegex {
    public static final int MAX_REGEX_LENGTH = 1024;
    public static final int MAX_LINE_LENGTH = 4096;
    public static final int MAX_PROGRAM_SIZE = 4096;
    public static final int MAX_CAPTURE_GROUPS = 32;
    public static final int MAX_MATCHES = 64;

    private final Pattern pattern;

    private ProfileRegex(Pattern pattern) {
        this.pattern = pattern;
    }

    public static ProfileRegex compile(String regex) {
        if (regex == null || regex.isBlank() || regex.length() > MAX_REGEX_LENGTH) {
            throw new ProfileDefinitionException(REGEX_LENGTH);
        }
        try {
            Pattern compiled = Pattern.compile(regex);
            if (compiled.programSize() > MAX_PROGRAM_SIZE || compiled.groupCount() > MAX_CAPTURE_GROUPS) {
                throw new ProfileDefinitionException(REGEX_COMPLEXITY);
            }
            if (compiled.matcher("").find()) {
                throw new ProfileDefinitionException(EMPTY_MATCH);
            }
            return new ProfileRegex(compiled);
        } catch (PatternSyntaxException exception) {
            throw new ProfileDefinitionException(REGEX_SYNTAX);
        }
    }

    public int groupCount() {
        return pattern.groupCount();
    }

    public List<Match> findAll(String text) {
        return findAll(text, new Budget());
    }

    public List<Match> findAll(String text, Budget budget) {
        if (text == null || text.length() > MAX_LINE_LENGTH) {
            throw new ProfileDefinitionException(INPUT_LIMIT);
        }
        List<Match> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        int previousEnd = 0;
        while (true) {
            // Captures may require a second NFA pass. Charge the remaining search span
            // on every find, including the final unsuccessful search, before executing it.
            budget.consume(2L * pattern.programSize() * (text.length() - previousEnd + 1));
            if (!matcher.find()) {
                return List.copyOf(matches);
            }
            if (matcher.start() == matcher.end()) {
                throw new ProfileDefinitionException(EMPTY_MATCH);
            }
            if (matches.size() == MAX_MATCHES) {
                throw new ProfileDefinitionException(EVALUATION_LIMIT);
            }
            Map<Integer, String> groups = new HashMap<>();
            for (int group = 0; group <= matcher.groupCount(); group++) {
                String value = matcher.group(group);
                if (value != null) {
                    groups.put(group, value);
                }
            }
            matches.add(new Match(matcher.start(), matcher.end(), groups));
            previousEnd = matcher.end();
        }
    }

    /** One fresh budget per complete profile evaluation, shared across all patterns/lines. */
    public static final class Budget {
        public static final long MAX_WORK = 16_777_216;
        private long remaining = MAX_WORK;

        private void consume(long work) {
            if (work > remaining) {
                throw new ProfileDefinitionException(EVALUATION_LIMIT);
            }
            remaining -= work;
        }
    }

    public record Match(int start, int end, Map<Integer, String> groups) {
        public Match {
            groups = Map.copyOf(groups);
        }

        public String group(int group) {
            return groups.get(group);
        }
    }
}
