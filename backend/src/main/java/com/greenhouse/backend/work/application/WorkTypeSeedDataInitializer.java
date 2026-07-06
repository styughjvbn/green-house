package com.greenhouse.backend.work.application;

import com.greenhouse.backend.work.domain.WorkType;
import com.greenhouse.backend.work.domain.WorkTypeTemplate;
import com.greenhouse.backend.work.repository.WorkTypeRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WorkTypeSeedDataInitializer implements CommandLineRunner {

	private static final List<DefaultWorkType> DEFAULT_TYPES = List.of(
		new DefaultWorkType("INBOUND", "\uc785\uace0", WorkTypeTemplate.MEMO, false, 0),
		new DefaultWorkType("POTTING", "\ud3ec\ud2b8 \uc791\uc5c5", WorkTypeTemplate.REPOT, false, 1),
		new DefaultWorkType("PESTICIDE", "\ub18d\uc57d", WorkTypeTemplate.PESTICIDE, false, 2),
		new DefaultWorkType("FERTILIZER", "\ube44\ub8cc", WorkTypeTemplate.FERTILIZER, false, 3),
		new DefaultWorkType("REPOT", "\ubd84\uac08\uc774", WorkTypeTemplate.REPOT, false, 4),
		new DefaultWorkType("STATUS", "\uc0c1\ud0dc \uae30\ub85d", WorkTypeTemplate.STATUS, false, 5),
		new DefaultWorkType("MEMO", "\uc77c\ubc18 \uba54\ubaa8", WorkTypeTemplate.MEMO, false, 6),
		new DefaultWorkType("LEAF_CLEANUP", "\uc78e \uc815\ub9ac", WorkTypeTemplate.CLEANUP, false, 7),
		new DefaultWorkType("WEED_CLEANUP", "\uc7a1\ucd08 \uc815\ub9ac", WorkTypeTemplate.CLEANUP, false, 8),
		new DefaultWorkType("FLOWER_CLEANUP", "\ub2e8\ud654/\uaf43 \uc815\ub9ac", WorkTypeTemplate.CLEANUP, false, 9),
		new DefaultWorkType(WorkType.MOVEMENT_CODE, "\uc704\uce58 \uc774\ub3d9", WorkTypeTemplate.MOVEMENT, true, 10)
	);

	private final WorkTypeRepository workTypeRepository;

	public WorkTypeSeedDataInitializer(WorkTypeRepository workTypeRepository) {
		this.workTypeRepository = workTypeRepository;
	}

	@Override
	@Transactional
	public void run(String... args) {
		for (DefaultWorkType defaultType : DEFAULT_TYPES) {
			workTypeRepository.findByCode(defaultType.code())
				.orElseGet(() -> workTypeRepository.save(new WorkType(
					defaultType.code(),
					defaultType.name(),
					defaultType.template(),
					true,
					defaultType.systemType(),
					true,
					defaultType.sortOrder()
				)));
		}
	}

	private record DefaultWorkType(
		String code,
		String name,
		WorkTypeTemplate template,
		boolean systemType,
		int sortOrder
	) {
	}
}
