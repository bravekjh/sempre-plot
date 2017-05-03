package edu.stanford.nlp.sempre.interactive.test;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.testng.Assert;
import org.testng.annotations.Test;

import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.Executor;
import edu.stanford.nlp.sempre.Formulas;
import edu.stanford.nlp.sempre.Json;
import edu.stanford.nlp.sempre.NaiveKnowledgeGraph;
import edu.stanford.nlp.sempre.StringValue;
import edu.stanford.nlp.sempre.interactive.DALExecutor;
import edu.stanford.nlp.sempre.interactive.Item;
import edu.stanford.nlp.sempre.interactive.World;
import edu.stanford.nlp.sempre.interactive.voxelurn.Color;
import edu.stanford.nlp.sempre.interactive.voxelurn.Voxel;
import fig.basic.LispTree;
import fig.basic.LogInfo;

/**
 * Tests the DALExecutor
 *
 * @author sidaw
 */

public class DALExecutorTest {
  DALExecutor executor = new DALExecutor();

  public static void runFormula(DALExecutor executor, String formula, ContextValue context,
      Predicate<World> checker) {
    LogInfo.begin_track("formula: %s", formula);
    DALExecutor.opts.worldType = "VoxelWorld";
    Executor.Response response = executor.execute(Formulas.fromLispTree(LispTree.proto.parseFromString(formula)),
        context);

    NaiveKnowledgeGraph graph = (NaiveKnowledgeGraph) context.graph;
    String wallString = ((StringValue) graph.triples.get(0).e1).value;
    String jsonStr = ((StringValue) response.value).value;
    LogInfo.logs("Start:\t%s", wallString);
    LogInfo.logs("Result:\t%s", jsonStr);
    LogInfo.end_track();

    if (checker != null) {
      if (!checker.test(World.fromContext("VoxelWorld", getContext(jsonStr)))) {
        LogInfo.end_track();
        Assert.fail(jsonStr);
      }
    }
  }

  private static ContextValue getContext(String blocks) {
    // a hack to pass in the world state without much change to the code
    String strigify2 = Json.writeValueAsStringHard(blocks); // some parsing
                                                            // issue inside
                                                            // lisptree parser
    return ContextValue.fromString(
        String.format("(context (graph NaiveKnowledgeGraph ((string \"%s\") (name b) (name c))))", strigify2));
  }

  public Predicate<World> selectedSize(int n) {
    return x -> {
      LogInfo.logs("Got %d, expected %d", x.selected().size(), n);
      return x.selected().size() == n;
    };
  }

  @Test(groups = { "Interactive" })
  public void testJoin() {
    String defaultBlocks = "[[1,1,1,\"Green\",[]],[1,2,1,\"Blue\",[]],[2,2,1,\"Red\",[]],[3,2,2,\"Yellow\",[]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testJoin");

    runFormula(executor, "(: select *)", context, selectedSize(4));
    runFormula(executor, "(: select (or (color red) (color green)))", context, selectedSize(2));
    runFormula(executor, "(: select (or (row (number 1)) (row (number 2))))", context, selectedSize(3));
    runFormula(executor, "(: select (col ((reverse row) (color red))))", context, null);
    runFormula(executor, "(: select (color ((reverse color) (row 3))))", context, null);
 
    runFormula(executor, "(: select (color ((reverse color) (color ((reverse color) (color red))))))", context,
        x -> x.selected().iterator().next().get("color").equals("red"));
    runFormula(executor, "(: select (and (row 1) (not (color green))))", context, x -> x.selected().isEmpty());
    LogInfo.end_track();

  }

  @Test(groups = { "Interactive" })
  public void testSpecialSets() {
    String defaultBlocks = "[[1,1,1,\"Green\",[\"S\"]],[1,2,1,\"Blue\",[\"S\"]],[2,2,1,\"Red\",[\"S\"]],[2,2,2,\"Yellow\",[]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testSpecial");
    runFormula(executor, "(: select nothing)", context, selectedSize(0));
    runFormula(executor, "(: select *)", context, selectedSize(4));
    runFormula(executor, "(: select this)", context, selectedSize(3));
    runFormula(executor, "(: select (and this nothing))", context, selectedSize(0));
    runFormula(executor, "(: select (not this))", context, selectedSize(1));

    LogInfo.end_track();
  }

  @Test(groups = { "Interactive" })
  public void testMerge() {
    {
      String defaultBlocks = "[[1,1,1,\"Green\",[]],[1,2,1,\"Blue\",[]],[2,2,1,\"Red\",[]],[2,2,2,\"Yellow\",[]]]";
      ContextValue context = getContext(defaultBlocks);
      LogInfo.begin_track("testMerge");
      runFormula(executor, "(: select (or (color red) (color blue)))", context, selectedSize(2));
      runFormula(executor, "(: select (and (color red) (color blue)))", context, selectedSize(0));
      runFormula(executor, "(: select (not (color red)))", context, selectedSize(3));
      runFormula(executor, "(: select (not *))", context, selectedSize(0));
    }
    LogInfo.end_track();
  }

  @Test(groups = { "Interactive" })
  public void testBasicActions() {
    String defaultBlocks = "[[1,1,1,\"Green\",[]],[1,2,1,\"Blue\",[]],[2,2,1,\"Red\",[]],[2,2,3,\"Yellow\",[]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testBasicActions");
    runFormula(executor, "(:s (: select *) (: remove))", context, x -> real(x.allItems).size() == 0);
    runFormula(executor, "(:s (: select (row (number 1))) (: add red top) (: add red top))", context,
        x -> real(x.allItems).size() == 8);
    runFormula(executor, "(:for * (: remove))", context, x -> real(x.allItems).size() == 0);
    runFormula(executor, "(:foreach * (: remove))", context, x -> real(x.allItems).size() == 0);
    runFormula(executor, "(:s (: select (or (color red) (color orange))) (: remove))", context,
        x -> real(x.allItems).size() == 3);
    runFormula(executor, "(:foreach (or (color red) (color orange)) (:loop (number 5) (: add red top)))", context,
        x -> x.allItems.size() == 9);
    runFormula(executor,
        "(:for (or (color red) (color blue)) (:loop (number 5) (:s (: move left) (: move right) (: move left))))",
        context, null);

    LogInfo.end_track();
  }

  private Set<Item> real(Set<Item> all) {
    return all.stream().filter(c -> !((Voxel) c).color.equals(Color.Fake)).collect(Collectors.toSet());
  }

  @Test(groups = { "Interactive" })
  public void testRemove() {
    // this is a green stick
    String defaultBlocks = "[[1,1,1,\"Green\",[]],[1,1,2,\"Green\",[\"S\"]],[1,1,3,\"Red\",[\"S\"]],[1,1,4,\"Green\",[]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testMoreActions");
    runFormula(executor, "(: remove)", context, x -> real(x.allItems).size() == 2 && x.selected.size() == 2);
    runFormula(executor, "(:for * (: remove))", context, x -> x.selected.size() == 2 && real(x.allItems).size() == 0);
    runFormula(executor, "(:for (color green) (: remove))", context,
        x -> x.selected.size() == 2 && real(x.allItems).size() == 1);

    LogInfo.end_track();
  }

  @Test(groups = { "Interactive" })
  public void testMoreActions() {
    // this is a green stick
    String defaultBlocks = "[[1,1,1,\"Green\",[]],[1,1,2,\"Green\",[]],[1,1,3,\"Green\",[]],[1,1,4,\"Green\",[]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testMoreActions");
    runFormula(executor, "(:s (: select *) (:for (call veryx left this) (: remove)))", context,
        x -> x.allItems.stream().allMatch(c -> ((Voxel) c).color.equals(Color.Fake)));
    runFormula(executor,
        "(:for * (:for (call veryx bot) (:loop (number 2) (:s (: add red left) (: select (call adj top))))))", context,
        x -> x.allItems.size() == 6);
    runFormula(executor, "(:s (: select *) (: select (call veryx bot selected)) (: remove selected) )", context,
        x -> real(x.allItems).size() == 3);
    // x -> x.selected().iterator().next().get("height") == new Integer(3)
    runFormula(executor, "(:loop (count (color green)) (: add red left *))", context, x -> x.allItems.size() == 20);

    LogInfo.end_track();
  }

  @Test(groups = { "Interactive" })
  public void troubleCases() {
    // this is a green stick
    String defaultBlocks = "[[1,1,1,\"Green\",[\"S\"]],[1,1,2,\"Green\",[]],[1,1,3,\"Green\",[]],[1,1,4,\"Green\",[]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("troubleCases");
    runFormula(executor, "(:s (: select *) (: select (or (call veryx top this) (call veryx bot this))))", context,
        selectedSize(2));
    runFormula(executor, " (: select (or (call veryx top (color green)) (call veryx bot (color green))))", context,
        selectedSize(2));
    runFormula(executor, " (: select (and (call veryx top (color green)) (call veryx bot (color green))))", context,
        selectedSize(0));
    runFormula(executor, " (: select (call adj top this))", context, selectedSize(1));
    LogInfo.end_track();
  }

  @Test(groups = { "Interactive" })
  public void testFake() {
    // this is a green stick
    String defaultBlocks = "[[1,1,0,\"Fake\",[\"S\"]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testAnchors");
    runFormula(executor, "(: add red top)", context, selectedSize(1));
    runFormula(executor, "(: add red left)", context, selectedSize(1));
    runFormula(executor, "(: add red here)", context, selectedSize(1));
    runFormula(executor, "(:loop (number 3) (: add red left))", context, selectedSize(1));
    runFormula(executor, "(:loop (number 3) (: add red top))", context, x -> x.allItems.size() == 4);
    runFormula(executor, "(:loop (number 3) (: add red left))", context, x -> x.allItems.size() == 4);
    runFormula(executor, "(:loop (number 3) (: select (or this (call adj top this))))", context, selectedSize(4));
    LogInfo.end_track();
  }

  @Test(groups = { "Interactive" })
  public void testIsolation() {
    // this is a green stick
    String defaultBlocks = "[[1,1,1,\"Green\",[\"S\"]],[1,1,2,\"Green\",[]],[1,1,3,\"Green\",[]],[1,1,4,\"Green\",[]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testIsolation");
    runFormula(executor, "(:isolate (:loop (number 4) (: add red top)))", context, x -> x.allItems.size() == 5);
    runFormula(executor, "(:isolate (:loop (number 2) (: add red top)))", context, x -> x.allItems.size() == 4);
    runFormula(executor, "(:isolate (:loop (number 5) (: add red top)))", context, x -> x.allItems.size() == 6);
    runFormula(executor, "(:s (:isolate (:loop (number 5) (: add red top))) (: select (color red)))", context,
        selectedSize(5));
    LogInfo.end_track();
  }

  @Test(groups = { "Interactive" })
  public void testUpdate() {
    String defaultBlocks = "[[1,1,1,\"Green\",[\"S\"]],[1,1,2,\"Red\",[]],[1,1,3,\"Green\",[]],[1,1,4,\"Green\",[]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testUpdate");
    runFormula(executor, "(:s (: update color red) (: select (color red)))", context, selectedSize(2));
    runFormula(executor, "(:s (: update height (number 0)) (: select (height  (number 0))))", context, selectedSize(1));
    LogInfo.end_track();
  }

  @Test(groups = { "Interactive" })
  public void testScoping() {
    // this is a green stick
    
    String defaultBlocks = "[[1,1,1,\"Green\",[\"S\"]],[1,1,2,\"Green\",[]],[1,1,3,\"Green\",[]],[1,1,4,\"Green\",[]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testIsolation");
    runFormula(executor, "(:blk (: select (call veryx top this)))", context,
        x -> (Integer) x.selected.iterator().next().get("height") == 1);
    runFormula(executor, "(:blkr (: select (call adj top this)))", context,
        x -> (Integer) x.selected.iterator().next().get("height") == 2);

    runFormula(executor,
        "(:s (:blk (: add red here ) (: select (call adj top this)) (: add red here )) (: select (color red)))",
        context, x -> x.selected.size() == 2);
    runFormula(executor,
        "(:s (:blkr (: add red here ) (: select (call adj top this)) (: add red here )) (: select (color red)))",
        context, x -> x.selected.size() == 2);
    LogInfo.end_track();
  }
  
  @Test(groups = { "Interactive" })
  public void testUnsupported() {
    // a rare unsupported operations bug due to immutable sets
    String defaultBlocks = "[[1,1,1,\"fake\",[\"S\"]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testUnsuported");
    executor.opts.verbose = 1;
    runFormula(executor, "  (:isolate (:s (:isolate (:loop (number 3) (:s (: select (call adj front)) (: add yellow here)))) (:s (:isolate (:s (: select (call adj left)) (: add yellow here))) (:isolate (:s (:s (: select (call adj front)) (: add yellow here)) (:loop (number 2) (:s (: select (call adj left)) (: add yellow here))))) (:isolate (:s (:s (:isolate (: select (call adj back))) (:isolate (: select (call adj left)))) (: add yellow here))) (: select (color yellow)) (:loop (number 4) (: add yellow top)) (:loop (number 2) (: select (call adj top))) (: select (and (call adj left) (call adj front))) (:blk (:s (: select (call adj right)) (: move back))) (:blk (:s (:s (:isolate (: select (call adj right))) (:isolate (: select (call adj top)))) (: move back))) (:s (:s (:s (: select (call adj right)) (:loop (number 2) (: select (call adj top)))) (: select)) (: move back)) (:s (:s (:s (:s (:isolate (: select (call adj right))) (: select (call adj front))) (: select)) (: move back)) (:isolate (:s (:s (: select (call adj left)) (: select (call adj bot this))) (: add yellow here))) (:s (:s (:s (: select (call adj top)) (: select (call adj left))) (: select)) (: move back)) (:s (:s (:s (: select (call adj bot this)) (: select (call adj front))) (: select)) (: move back)) (:isolate (:s (:s (: select (call adj top)) (:loop (number 3) (: select (call adj right)))) (: add yellow here))) (:isolate (:s (:s (:isolate (: select (call adj front))) (:isolate (: select (call adj right)))) (: remove))) (:isolate (:s (:s (:isolate (: select (call adj front))) (:isolate (: select (call adj left)))) (: remove))) (:s (:s (:s (:isolate (:s (: select (call adj left)) (: select (call adj left)))) (:isolate (: select (call adj left)))) (:s (: select (call adj back)) (: select (call adj back)))) (: move back)) (: select (color yellow))))))",
        context,
        x -> true);
    executor.opts.verbose = 0;
    LogInfo.end_track();
  }
  
  @Test(groups = { "Interactive" })
  public void testBasicSub() {
    // a rare unsupported operations bug
    String defaultBlocks = "[[1,1,1,\"fake\",[\"S\"]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testUnsuported");

    // def adds red_tower to symbol table
    runFormula(executor, " (:def red_tower (:loop (number 3) (: add (name red color) (name top dir))))",
        context, x -> true);
    runFormula(executor, "red_tower",
        context, x -> x.allItems.size() == 4 && 
        x.allItems.stream().allMatch(i -> ((Voxel)i).color.equals(Color.fromString("red"))));
    runFormula(executor, " (:sub red_tower red yellow)",
        context, x -> x.allItems.size() == 4 && 
        x.allItems.stream().allMatch(i -> ((Voxel)i).color.equals(Color.fromString("yellow"))));
    runFormula(executor, " (:sub red_tower 3 5)",
        context, x -> x.allItems.size() == 6 && 
        x.allItems.stream().allMatch(i -> ((Voxel)i).color.equals(Color.fromString("red"))));
    runFormula(executor, "(:sub (:sub red_tower 3 5) red yellow)",
        context, x -> x.allItems.size() == 6 && 
        x.allItems.stream().allMatch(i -> ((Voxel)i).color.equals(Color.fromString("yellow"))));

    LogInfo.end_track();
  }
  
  @Test(groups = { "Interactive" })
  public void testRecursiveSub() {
    // a rare unsupported operations bug
    String defaultBlocks = "[[1,1,1,\"fake\",[\"S\"]]]";
    ContextValue context = getContext(defaultBlocks);
    LogInfo.begin_track("testUnsuported");

    // def adds red_tower to symbol table
    runFormula(executor, " (defun trunk (:loop (number 5) (:s (: add red here) (call adj top this))))",
        context, x -> true);
    runFormula(executor, " (defun leaves (:s (: select (or (call adj left this) (call adj right this))) (: add green here)))",
        context, x -> true);
    runFormula(executor, " (defun tree (: trunk leaves))",
        context, x -> true);
    
    runFormula(executor, " (:s trunk leaves)",
        context, x -> x.allItems.size() == 7);
    runFormula(executor, " (:s tree)",
        context, x -> x.allItems.size() == 7);
    
    // red tree
    runFormula(executor, " (:sub tree col red)",
        context,
        x -> x.allItems.stream().filter(i -> ((Voxel)i).color.equals(Color.fromString("red"))).count() == 2 );
    
    // red tree
    runFormula(executor, " (:sub tree green red)",
        context,
        x -> x.allItems.stream().filter(i -> ((Voxel)i).color.equals(Color.fromString("red"))).count() == 7 );
    
     // tree with red trunk
    runFormula(executor, " (:sub tree trunk (:sub trunk * red))", // should be same as previous
        context,
        x -> x.allItems.stream().filter(i -> ((Voxel)i).color.equals(Color.fromString("red"))).count() == 5 );
    
    // tree with red trunk and red leaves
    runFormula(executor, " (:sub (:sub tree trunk (:sub trunk * red)) leaves (:sub leaves * red))", // should be same as previous
        context,
        x -> x.allItems.stream().filter(i -> ((Voxel)i).color.equals(Color.fromString("red"))).count() == 7 );

    
    // red tree with green leaves
    runFormula(executor, " (:sub (:sub tree * red) * (:sub leaves * green))", // should be same as previous
        context,
        x -> x.allItems.stream().filter(i -> ((Voxel)i).color.equals(Color.fromString("red"))).count() == 5 );
    
    LogInfo.end_track();
  }
}
