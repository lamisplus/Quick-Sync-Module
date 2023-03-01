package org.lamisplus.modules.sync.extensions;

import com.foreach.across.core.annotations.ModuleConfiguration;
import com.foreach.across.modules.hibernate.provider.HibernatePackageConfigurer;
import com.foreach.across.modules.hibernate.provider.HibernatePackageRegistry;
import lombok.extern.slf4j.Slf4j;
import org.lamisplus.modules.patient.domain.PatientDomain;
import org.lamisplus.modules.sync.domain.SyncDomain;
import org.lamisplus.modules.triage.domain.TriageDomain;
import org.springframework.context.annotation.Configuration;


@Slf4j
@ModuleConfiguration({"AcrossHibernateJpaModule"})
@Configuration
public class EntityScanConfiguration implements HibernatePackageConfigurer {


    @Override
    public void configureHibernatePackage(HibernatePackageRegistry hibernatePackage) {
        hibernatePackage.addPackageToScan (SyncDomain.class,PatientDomain.class,TriageDomain.class);
    }
}
