package com.greenhouse.backend.sales.document.application;

import com.greenhouse.backend.BackendApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Explicit offline operations; starting the application never deletes photos. */
public final class SalesPhotoStorageCli {
  private SalesPhotoStorageCli() {}

  public static void main(String[] args) {
    // Maintenance requires an already-upgraded schema and must never recreate it.
    System.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
    System.setProperty("spring.flyway.enabled", "false");
    try (var app =
        new SpringApplicationBuilder(BackendApplication.class)
            .web(WebApplicationType.NONE)
            .run(args)) {
      var service = app.getBean(SalesPhotoMaintenanceService.class);
      var environment = app.getEnvironment();
      String command = environment.getRequiredProperty("photo-storage.command");
      switch (command) {
        case "verify" -> System.out.println("verified=" + service.verify());
        case "orphans" ->
            System.out.println("unreferenced_older_than_24h=" + service.orphans(false));
        case "cleanup" -> {
          if (!environment.getProperty("photo-storage.writers-stopped", Boolean.class, false))
            throw new IllegalArgumentException(
                "Stop all photo writers and pass --photo-storage.writers-stopped=true");
          System.out.println("deleted=" + service.orphans(true));
        }
        default -> throw new IllegalArgumentException("Unknown photo storage operation");
      }
    }
  }
}
