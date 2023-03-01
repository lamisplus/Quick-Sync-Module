 package org.lamisplus.modules.sync.installers;

 import com.foreach.across.core.annotations.Installer;
 import com.foreach.across.core.installers.AcrossLiquibaseInstaller;
 import org.springframework.core.annotation.Order;

 @Order(1)
 @Installer(name = "schema-installer",
         description = "Installs the required database tables",
         version = 1)
 public class QuickSyncInstaller extends AcrossLiquibaseInstaller {
     public QuickSyncInstaller() {
         super("classpath:installers/sync/schema/schema.xml");
     }
 }
