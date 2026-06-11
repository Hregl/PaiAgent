package com.paiagent.model.entity;

import lombok.Data;

import jakarta.persistence.*;

@Data
@Entity
@Table(name = "app_configs")
public class AppConfig {
    @Id
    @Column(length = 100)
    private String configKey;

    @Column(name = "config_value", length = 500)
    private String configValue;
}
