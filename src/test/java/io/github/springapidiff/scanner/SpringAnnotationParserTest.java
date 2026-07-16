package io.github.springapidiff.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.AnnotationExpr;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpringAnnotationParserTest {
    private final SpringAnnotationParser parser = new SpringAnnotationParser();

    @Test
    void parsesMultiplePathsForFixedMethodMapping() {
        List<SpringAnnotationParser.MappingInfo> mappings = parser.mappings(annotation("@GetMapping({\"/users\", \"/members\"})"));

        assertThat(mappings).extracting(SpringAnnotationParser.MappingInfo::method, SpringAnnotationParser.MappingInfo::path)
            .containsExactly(tuple("GET", "/users"), tuple("GET", "/members"));
    }

    @Test
    void parsesEveryMethodAndPathCombination() {
        List<SpringAnnotationParser.MappingInfo> mappings = parser.mappings(annotation(
            "@RequestMapping(path = {\"/users\", \"/members\"}, method = {RequestMethod.GET, RequestMethod.POST})"));

        assertThat(mappings).extracting(SpringAnnotationParser.MappingInfo::method, SpringAnnotationParser.MappingInfo::path)
            .containsExactly(
                tuple("GET", "/users"),
                tuple("GET", "/members"),
                tuple("POST", "/users"),
                tuple("POST", "/members"));
    }

    @Test
    void usesRootPathWhenMappingHasNoPath() {
        assertThat(parser.mappings(annotation("@PostMapping")))
            .extracting(SpringAnnotationParser.MappingInfo::method, SpringAnnotationParser.MappingInfo::path)
            .containsExactly(tuple("POST", "/"));
    }

    @Test
    void representsRequestMappingWithoutMethodAsAny() {
        assertThat(parser.mappings(annotation("@RequestMapping(\"/users\")")))
            .extracting(SpringAnnotationParser.MappingInfo::method, SpringAnnotationParser.MappingInfo::path)
            .containsExactly(tuple("ANY", "/users"));
    }

    private AnnotationExpr annotation(String source) {
        return StaticJavaParser.parseAnnotation(source);
    }
}
