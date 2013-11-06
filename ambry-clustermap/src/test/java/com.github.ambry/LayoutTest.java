package com.github.ambry;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 *
 */
public class LayoutTest {

  @Test
  public void jsonSerDeTest() {
    Cluster cluster = TestUtils.buildCluster("Alpha");
    Layout layoutSer = TestUtils.buildLayout(cluster);

    layoutSer.validate();
    // System.out.println(layoutSer.toString());

    try {
      JSONObject jsonObject = new JSONObject(layoutSer.toString());
      Layout layoutDe = new Layout(cluster, jsonObject);

      assertEquals(layoutSer, layoutDe);

      // "2" and "4" are based on hard-coded constants in buildCluster
      assertEquals(2, layoutDe.getPartitions().size());
      for (Partition partitionDe : layoutDe.getPartitions()) {
        assertEquals(4, partitionDe.getReplicas().size());
      }
    } catch (JSONException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      fail();
    }
  }


}
