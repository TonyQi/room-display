package local.rooms;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Desktop-only ownership: when the EXE exits or crashes, close the database and stop. */
@Component
public class DesktopLifecycle {
    private final ConfigurableApplicationContext context;
    public DesktopLifecycle(ConfigurableApplicationContext context) { this.context=context; }
    @EventListener(ApplicationReadyEvent.class)
    public void startOwnerMonitor() {
        if (!"true".equals(System.getenv("ROOM_DESKTOP_MODE"))) return;
        Thread monitor=new Thread(() -> {
            try {
                BufferedReader reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
                String line;
                while ((line=reader.readLine())!=null) if ("shutdown".equals(line)) break;
            } catch (Exception ignored) { /* A closed owner pipe also means exit. */ }
            int exit=SpringApplication.exit(context,() -> 0);
            System.exit(exit);
        },"desktop-owner-monitor");
        monitor.setDaemon(true);
        monitor.start();
    }
}
