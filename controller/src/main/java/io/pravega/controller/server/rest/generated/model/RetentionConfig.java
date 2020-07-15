/*
 * Pravega Controller APIs
 * List of admin REST APIs for the pravega controller service.
 *
 * OpenAPI spec version: 0.0.1
 * 
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


package io.pravega.controller.server.rest.generated.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.pravega.controller.server.rest.generated.model.TimeBasedRetention;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.*;

/**
 * RetentionConfig
 */

public class RetentionConfig   {
  /**
   * Indicates if retention is by space or time.
   */
  public enum TypeEnum {
    LIMITED_DAYS("LIMITED_DAYS"),

    LIMITED_SIZE_MB("LIMITED_SIZE_MB");

    private String value;

    TypeEnum(String value) {
      this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static TypeEnum fromValue(String text) {
      for (TypeEnum b : TypeEnum.values()) {
        if (String.valueOf(b.value).equals(text)) {
          return b;
        }
      }
      return null;
    }
  }

  @JsonProperty("type")
  private TypeEnum type = null;

  @JsonProperty("value")
  private Long value = null;

  @JsonProperty("timeBasedRetention")
  private TimeBasedRetention timeBasedRetention = null;

  public RetentionConfig type(TypeEnum type) {
    this.type = type;
    return this;
  }

  /**
   * Indicates if retention is by space or time.
   * @return type
   **/
  @JsonProperty("type")
  @ApiModelProperty(value = "Indicates if retention is by space or time.")
  public TypeEnum getType() {
    return type;
  }

  public void setType(TypeEnum type) {
    this.type = type;
  }

  public RetentionConfig value(Long value) {
    this.value = value;
    return this;
  }

  /**
   * Get value
   * @return value
   **/
  @JsonProperty("value")
  @ApiModelProperty(value = "")
  public Long getValue() {
    return value;
  }

  public void setValue(Long value) {
    this.value = value;
  }

  public RetentionConfig timeBasedRetention(TimeBasedRetention timeBasedRetention) {
    this.timeBasedRetention = timeBasedRetention;
    return this;
  }

  /**
   * Get timeBasedRetention
   * @return timeBasedRetention
   **/
  @JsonProperty("timeBasedRetention")
  @ApiModelProperty(value = "")
  public TimeBasedRetention getTimeBasedRetention() {
    return timeBasedRetention;
  }

  public void setTimeBasedRetention(TimeBasedRetention timeBasedRetention) {
    this.timeBasedRetention = timeBasedRetention;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RetentionConfig retentionConfig = (RetentionConfig) o;
    return Objects.equals(this.type, retentionConfig.type) &&
        Objects.equals(this.value, retentionConfig.value) &&
        Objects.equals(this.timeBasedRetention, retentionConfig.timeBasedRetention);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, value, timeBasedRetention);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class RetentionConfig {\n");
    
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    value: ").append(toIndentedString(value)).append("\n");
    sb.append("    timeBasedRetention: ").append(toIndentedString(timeBasedRetention)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

