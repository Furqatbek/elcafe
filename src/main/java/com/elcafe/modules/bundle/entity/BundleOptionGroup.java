package com.elcafe.modules.bundle.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bundle_option_groups")
public class BundleOptionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bundle_id", nullable = false)
    @ToString.Exclude
    private Bundle bundle;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "min_selections")
    @Builder.Default
    private Integer minSelections = 1;

    @Column(name = "max_selections")
    @Builder.Default
    private Integer maxSelections = 1;

    @Column(name = "is_required")
    @Builder.Default
    private Boolean isRequired = true;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @OneToMany(mappedBy = "optionGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<BundleOption> options = new ArrayList<>();

    // Helper methods
    public void addOption(BundleOption option) {
        options.add(option);
        option.setOptionGroup(this);
    }

    public void removeOption(BundleOption option) {
        options.remove(option);
        option.setOptionGroup(null);
    }
}
