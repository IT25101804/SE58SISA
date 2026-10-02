package sisa.config;

import sisa.entity.NotificationScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NotificationScopeConstraintFix implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NotificationScopeConstraintFix.class);
    private static final String CONSTRAINT = "ck_notifications_target_scope";

    private final JdbcTemplate jdbc;

    public NotificationScopeConstraintFix(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        String product = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        if (product == null || !product.toLowerCase().contains("sql server")) return;

        List<String[]> constraints = jdbc.query(
                "SELECT cc.name, cc.definition FROM sys.check_constraints cc "
                        + "JOIN sys.columns c ON c.object_id = cc.parent_object_id AND c.column_id = cc.parent_column_id "
                        + "WHERE cc.parent_object_id = OBJECT_ID('notifications') AND c.name = 'target_scope'",
                (rs, i) -> new String[]{rs.getString(1), rs.getString(2)});

        boolean upToDate = !constraints.isEmpty() && constraints.stream().allMatch(c ->
                Arrays.stream(NotificationScope.values()).allMatch(v -> c[1].contains("'" + v.name() + "'")));
        if (upToDate) return;

        for (String[] c : constraints) {
            jdbc.execute("ALTER TABLE notifications DROP CONSTRAINT [" + c[0].replace("]", "]]") + "]");
        }
        String allowed = Arrays.stream(NotificationScope.values())
                .map(v -> "'" + v.name() + "'")
                .collect(Collectors.joining(", "));
        jdbc.execute("ALTER TABLE notifications ADD CONSTRAINT " + CONSTRAINT
                + " CHECK (target_scope IN (" + allowed + "))");
        log.info("Updated notifications.target_scope check constraint to allow: {}", allowed);
    }
}
