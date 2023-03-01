package org.lamisplus.modules.sync.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.codehaus.jackson.annotate.JsonIgnore;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quick_sync_history")
@NoArgsConstructor
@Setter
@Getter
public class QuickSyncHistory {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id", nullable = false)
	@JsonIgnore
	private Long id;
	private String filename;
	private String tableName;
	private String facilityName;
	private LocalDateTime dateCreated;
	Integer fileSize;
	private String status;
}