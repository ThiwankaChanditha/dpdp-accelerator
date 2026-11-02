package org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.Event;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.Pagination;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class EventPage  {
  
  @ApiModelProperty(required = true, value = "")

  private List<Event> items;

  @ApiModelProperty(required = true, value = "")

  private Pagination pagination;
 /**
   * Get items
   * @return items
  **/
  @JsonProperty("items")
  public List<Event> getItems() {
    return items;
  }

  public void setItems(List<Event> items) {
    this.items = items;
  }

  public EventPage items(List<Event> items) {
    this.items = items;
    return this;
  }

  public EventPage addItemsItem(Event itemsItem) {
    this.items.add(itemsItem);
    return this;
  }

 /**
   * Get pagination
   * @return pagination
  **/
  @JsonProperty("pagination")
  public Pagination getPagination() {
    return pagination;
  }

  public void setPagination(Pagination pagination) {
    this.pagination = pagination;
  }

  public EventPage pagination(Pagination pagination) {
    this.pagination = pagination;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EventPage eventPage = (EventPage) o;
    return Objects.equals(this.items, eventPage.items) &&
        Objects.equals(this.pagination, eventPage.pagination);
  }

  @Override
  public int hashCode() {
    return Objects.hash(items, pagination);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EventPage {\n");
    
    sb.append("    items: ").append(toIndentedString(items)).append("\n");
    sb.append("    pagination: ").append(toIndentedString(pagination)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private static String toIndentedString(Object o) {
    return o == null ? "null" : o.toString().replace("\n", "\n    ");
  }
}
