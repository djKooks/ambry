package com.github.ambry;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 *
 */
public class ReplicaIdTest {

  @Test
  public void jsonSerDeTest() {
    ReplicaId replicaIdSer = TestUtils.getNewReplicaId();
    // System.out.println(replicaIdSer.toString());

    try {
      JSONObject jsonObject = new JSONObject(replicaIdSer.toString());

      ReplicaId replicaIdDe = new ReplicaId(jsonObject);

      assertEquals(replicaIdSer, replicaIdDe);
    } catch (JSONException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      fail();
    }

  }

}
