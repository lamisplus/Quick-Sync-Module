package org.lamisplus.modules.sync;

import com.foreach.across.config.AcrossApplication;
import com.foreach.across.core.AcrossModule;
import com.foreach.across.core.context.configurer.ComponentScanConfigurer;

@AcrossApplication(
		modules = {
		})
public class SyncModule extends AcrossModule {
	public static final String NAME = "SyncModule";

	public SyncModule() {
		super ();
		addApplicationContextConfigurer (new ComponentScanConfigurer(
				getClass ().getPackage ().getName () + ".repository",
				getClass ().getPackage ().getName () + ".service",
				getClass ().getPackage ().getName () + ".mapper",
				getClass ().getPackage ().getName () + ".controller"
		));
	}
	@Override
	public String getName() {
		return NAME;
	}
}
