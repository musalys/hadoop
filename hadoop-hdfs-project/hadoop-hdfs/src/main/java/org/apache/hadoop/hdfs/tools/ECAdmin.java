/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.hadoop.hdfs.tools;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.hdfs.protocol.ErasureCodingPolicy;
import org.apache.hadoop.tools.TableListing;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * CLI for the erasure code encoding operations.
 */
@InterfaceAudience.Private
public class ECAdmin extends Configured implements Tool {

  public static final String NAME = "ec";

  public static void main(String[] args) throws Exception {
    final ECAdmin admin = new ECAdmin(new Configuration());
    int res = ToolRunner.run(admin, args);
    System.exit(res);
  }

  public ECAdmin(Configuration conf) {
    super(conf);
  }

  @Override
  public int run(String[] args) throws Exception {
    if (args.length == 0) {
      AdminHelper.printUsage(false, NAME, COMMANDS);
      ToolRunner.printGenericCommandUsage(System.err);
      return 1;
    }
    final AdminHelper.Command command = AdminHelper.determineCommand(args[0],
        COMMANDS);
    if (command == null) {
      System.err.println("Can't understand command '" + args[0] + "'");
      if (!args[0].startsWith("-")) {
        System.err.println("Command names must start with dashes.");
      }
      AdminHelper.printUsage(false, NAME, COMMANDS);
      ToolRunner.printGenericCommandUsage(System.err);
      return 1;
    }
    final List<String> argsList = new LinkedList<>();
    argsList.addAll(Arrays.asList(args).subList(1, args.length));
    try {
      return command.run(getConf(), argsList);
    } catch (IllegalArgumentException e) {
      System.err.println(AdminHelper.prettifyException(e));
      return -1;
    }
  }

  /** Command to list the set of available erasure coding policies */
  private static class ListECPoliciesCommand
      implements AdminHelper.Command {
    @Override
    public String getName() {
      return "-listPolicies";
    }

    @Override
    public String getShortUsage() {
      return "[" + getName() + "]\n";
    }

    @Override
    public String getLongUsage() {
      return getShortUsage() + "\n" +
          "Get the list of supported erasure coding policies.\n";
    }

    @Override
    public int run(Configuration conf, List<String> args) throws IOException {
      if (args.size() > 0) {
        System.err.println(getName() + ": Too many arguments");
        return 1;
      }

      final DistributedFileSystem dfs = AdminHelper.getDFS(conf);
      try {
        Collection<ErasureCodingPolicy> policies =
            dfs.getAllErasureCodingPolicies();
        System.out.println("Erasure Coding Policies:");
        for (ErasureCodingPolicy policy : policies) {
          if (policy != null) {
            System.out.println("\t" + policy.getName());
          }
        }
      } catch (IOException e) {
        System.err.println(AdminHelper.prettifyException(e));
        return 2;
      }
      return 0;
    }
  }

  /** Command to get the erasure coding policy for a file or directory */
  private static class GetECPolicyCommand implements AdminHelper.Command {
    @Override
    public String getName() {
      return "-getPolicy";
    }

    @Override
    public String getShortUsage() {
      return "[" + getName() + " -path <path>]\n";
    }

    @Override
    public String getLongUsage() {
      final TableListing listing = AdminHelper.getOptionDescriptionListing();
      listing.addRow("<path>",
          "The path of the file/directory for getting the erasure coding " +
              "policy");
      return getShortUsage() + "\n" +
          "Get the erasure coding policy of a file/directory.\n\n" +
          listing.toString();
    }

    @Override
    public int run(Configuration conf, List<String> args) throws IOException {
      final String path = StringUtils.popOptionWithArgument("-path", args);
      if (path == null) {
        System.err.println("Please specify the path with -path.\nUsage: " +
            getLongUsage());
        return 1;
      }

      if (args.size() > 0) {
        System.err.println(getName() + ": Too many arguments");
        return 1;
      }

      final Path p = new Path(path);
      final DistributedFileSystem dfs = AdminHelper.getDFS(p.toUri(), conf);
      try {
        ErasureCodingPolicy ecPolicy = dfs.getErasureCodingPolicy(p);
        if (ecPolicy != null) {
          System.out.println(ecPolicy.getName());
        } else {
          System.out.println("The erasure coding policy of " + path + " is " +
              "unspecified");
        }
      } catch (Exception e) {
        System.err.println(AdminHelper.prettifyException(e));
        return 2;
      }
      return 0;
    }
  }

  /** Command to set the erasure coding policy to a file/directory */
  private static class SetECPolicyCommand implements AdminHelper.Command {
    @Override
    public String getName() {
      return "-setPolicy";
    }

    @Override
    public String getShortUsage() {
      return "[" + getName() + " -path <path> -policy <policy>]\n";
    }

    @Override
    public String getLongUsage() {
      TableListing listing = AdminHelper.getOptionDescriptionListing();
      listing.addRow("<path>", "The path of the file/directory to set " +
          "the erasure coding policy");
      listing.addRow("<policy>", "The name of the erasure coding policy");
      return getShortUsage() + "\n" +
          "Set the erasure coding policy for a file/directory.\n\n" +
          listing.toString();
    }

    @Override
    public int run(Configuration conf, List<String> args) throws IOException {
      final String path = StringUtils.popOptionWithArgument("-path", args);
      if (path == null) {
        System.err.println("Please specify the path for setting the EC " +
            "policy.\nUsage: " + getLongUsage());
        return 1;
      }

      final String ecPolicyName = StringUtils.popOptionWithArgument("-policy",
          args);
      if (ecPolicyName == null) {
        System.err.println("Please specify the policy name.\nUsage: " +
            getLongUsage());
        return 1;
      }

      if (args.size() > 0) {
        System.err.println(getName() + ": Too many arguments");
        return 1;
      }

      final Path p = new Path(path);
      final DistributedFileSystem dfs = AdminHelper.getDFS(p.toUri(), conf);
      try {
        dfs.setErasureCodingPolicy(p, ecPolicyName);
        System.out.println("Set erasure coding policy " + ecPolicyName +
            " on " + path);
      } catch (Exception e) {
        System.err.println(AdminHelper.prettifyException(e));
        return 2;
      }
      return 0;
    }
  }

  /** Command to unset the erasure coding policy set for a file/directory */
  private static class UnsetECPolicyCommand
      implements AdminHelper.Command {

    @Override
    public String getName() {
      return "-unsetPolicy";
    }

    @Override
    public String getShortUsage() {
      return "[" + getName() + " -path <path>]\n";
    }

    @Override
    public String getLongUsage() {
      TableListing listing = AdminHelper.getOptionDescriptionListing();
      listing.addRow("<path>", "The path of the directory "
          + "from which the erasure coding policy will be unset.");
      return getShortUsage() + "\n"
          + "Unset the erasure coding policy for a directory.\n\n"
          + listing.toString();
    }

    @Override
    public int run(Configuration conf, List<String> args) throws IOException {
      final String path = StringUtils.popOptionWithArgument("-path", args);
      if (path == null) {
        System.err.println("Please specify a path.\nUsage: " + getLongUsage());
        return 1;
      }

      if (args.size() > 0) {
        System.err.println(getName() + ": Too many arguments");
        return 1;
      }

      final Path p = new Path(path);
      final DistributedFileSystem dfs = AdminHelper.getDFS(p.toUri(), conf);
      try {
        dfs.unsetErasureCodingPolicy(p);
        System.out.println("Unset erasure coding policy from " + path);
      } catch (Exception e) {
        System.err.println(AdminHelper.prettifyException(e));
        return 2;
      }
      return 0;
    }
  }

  private static final AdminHelper.Command[] COMMANDS = {
      new ListECPoliciesCommand(),
      new GetECPolicyCommand(),
      new SetECPolicyCommand(),
      new UnsetECPolicyCommand()
  };
}
