/*
 * Copyright (c) 2008, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.btrace.runtime;

import com.sun.btrace.AnyType;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.ProbeClassName;
import com.sun.btrace.annotations.ProbeMethodName;
import com.sun.btrace.annotations.Where;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.Type;
import com.sun.btrace.util.LocalVariablesSorter;
import com.sun.btrace.util.TimeStampGenerator;
import com.sun.btrace.util.TimeStampHelper;
import java.util.regex.PatternSyntaxException;
import static com.sun.btrace.runtime.Constants.*;

/**
 * This instruments a probed class with BTrace probe
 * action class.
 *使用asm转换目标类和方法
 * @author A. Sundararajan
 */
public class Instrumentor extends ClassVisitor {
    private String btraceClassName;
    private ClassReader btraceClass;
    private List<OnMethod> onMethods;
    private List<OnMethod> applicableOnMethods;
    private Set<OnMethod> calledOnMethods;
    private String className, superName;
    private Class clazz;

    private boolean usesTimeStamp = false;
    private boolean timeStampExisting = false;


    public Instrumentor(Class clazz,
            String btraceClassName, ClassReader btraceClass,
            List<OnMethod> onMethods, ClassVisitor cv) {
        super(ASM4, cv);
        this.clazz = clazz;
        this.btraceClassName = btraceClassName.replace('.', '/');
        this.btraceClass = btraceClass;
        this.onMethods = onMethods;
        this.applicableOnMethods = new ArrayList<OnMethod>();
        this.calledOnMethods = new HashSet<OnMethod>();
    }

    public Instrumentor(Class clazz,
            String btraceClassName, byte[] btraceCode,
            List<OnMethod> onMethods, ClassVisitor cv) {
        this(clazz, btraceClassName, new ClassReader(btraceCode), onMethods, cv);
    }

    final public boolean hasMatch() {
        return !calledOnMethods.isEmpty();
    }

    public void visit(int version, int access, String name,
        String signature, String superName, String[] interfaces) {
        usesTimeStamp = false;
        timeStampExisting = false;
        //被转换的类名
        className = name;
        this.superName = superName;
        // we filter the probe methods applicable for this particular
        // class by brute force walking. FIXME: should I optimize?
        String externalName = name.replace('/', '.');
        for (OnMethod om : onMethods) {
            //获取需要探测的类名
            String probeClazz = om.getClazz();
            if (probeClazz.length() == 0) {
                continue;
            }
            char firstChar = probeClazz.charAt(0);
            //开始匹配类
            if (firstChar == '/' &&
                REGEX_SPECIFIER.matcher(probeClazz).matches()) {
                probeClazz = probeClazz.substring(1, probeClazz.length() - 1);
                try {
                    if (externalName.matches(probeClazz)) {
                        applicableOnMethods.add(om);
                    }
                } catch (PatternSyntaxException pse) {
                    reportPatternSyntaxException(probeClazz);
                }
            } else if (firstChar == '+') {
                // super type being matched.
                String superType = probeClazz.substring(1);
                // internal name of super type.
                String superTypeInternal = superType.replace('.', '/');
                /*
                 * If we are redefining a class, then we have a Class object
                 * of it and we can walk through it's hierarchy to match for
                 * specified super type. But, if we are loading it a fresh, then
                 * we can not walk through super hierarchy. We just check the
                 * immediate super class and directly implemented interfaces
                 */
                if (ClassFilter.isSubTypeOf(this.clazz, superType) ||
                    superName.equals(superTypeInternal) ||
                    isInArray(interfaces, superTypeInternal)) {
                    applicableOnMethods.add(om);
                }
            } else if (probeClazz.equals(externalName)) {
                applicableOnMethods.add(om);
            }
        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

    //被适配类上的注解
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        AnnotationVisitor av = super.visitAnnotation(desc, visible);
        String extName = Type.getType(desc).getClassName();
        for (OnMethod om : onMethods) {
            String probeClazz = om.getClazz();
            if (probeClazz.length() > 0 && probeClazz.charAt(0) == '@') {
                probeClazz = probeClazz.substring(1);
                if (probeClazz.length() == 0) {
                    continue;
                }
                if (REGEX_SPECIFIER.matcher(probeClazz).matches()) {
                    probeClazz = probeClazz.substring(1, probeClazz.length() - 1);
                    try {
                        if (extName.matches(probeClazz)) {
                            applicableOnMethods.add(om);
                        }
                    } catch (PatternSyntaxException pse) {
                        reportPatternSyntaxException(probeClazz);
                    }
                } else if (probeClazz.equals(extName)) {
                    applicableOnMethods.add(om);
                }
            }
        }
        return av;
    }

    //被适配类的方法
    public MethodVisitor visitMethod(final int access, final String name,
        final String desc, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, desc,
                signature, exceptions);
        //过滤不符合条件的方法，抽象、本地和btrace本身的方法
        if (applicableOnMethods.isEmpty() ||
            (access & ACC_ABSTRACT) != 0    ||
            (access & ACC_NATIVE) != 0      ||
            name.startsWith(BTRACE_METHOD_PREFIX)) {
            return methodVisitor;
        }
        //已经存在时间戳方法
        if (name.equals(TimeStampHelper.TIME_STAMP_NAME)) {
            timeStampExisting = true;
            return methodVisitor;
        }

        // used to create new local variables while keeping the class internals consistent
        // Call "int index = lvs.newVar(<type>)" to create a new local variable.
        // Then use the generated index to get hold of the variable
        //本地变量备份
        LocalVariablesSorter.Memento externalState = new LocalVariablesSorter.Memento();
        final LocalVariablesSorter lvs = new LocalVariablesSorter(access, desc, methodVisitor, externalState);
        methodVisitor = lvs;

        final int[] tsIndex = new int[]{-1, -1};
        //循环脚本中被触探的方法
        for (OnMethod om : applicableOnMethods) {
            //访问的是源码行号
            if (om.getLocation().getValue() == Kind.LINE) {
                methodVisitor = instrumentorFor(om, methodVisitor, lvs, tsIndex, access, name, desc);
            } else {
                String methodName = om.getMethod();
                if (methodName.equals("")) {
                    methodName = om.getTargetName();
                }
                //方法名字匹配且描述符匹配
                if (methodName.equals(name) &&
                    typeMatches(om.getType(), desc)) {
                    methodVisitor = instrumentorFor(om, methodVisitor, lvs, tsIndex, access, name, desc);
                } else if (methodName.charAt(0) == '/' &&
                           REGEX_SPECIFIER.matcher(methodName).matches()) {
                    methodName = methodName.substring(1, methodName.length() - 1);
                    try {
                        if (name.matches(methodName) &&
                            typeMatches(om.getType(), desc)) {
                            methodVisitor = instrumentorFor(om, methodVisitor, lvs, tsIndex, access, name, desc);
                        }
                    } catch (PatternSyntaxException pse) {
                        reportPatternSyntaxException(name);
                    }
                }
//                lvs[0] = new LocalVariablesSorter(access, desc, methodVisitor, externalState);
            }
        }

        return new MethodVisitor(ASM4,
                    methodVisitor) {
            public AnnotationVisitor visitAnnotation(String annoDesc,
                                  boolean visible) {
                for (OnMethod om : applicableOnMethods) {
                    String extAnnoName = Type.getType(annoDesc).getClassName();
                    String annoName = om.getMethod();
                    if (annoName.length() > 0 && annoName.charAt(0) == '@') {
                        annoName = annoName.substring(1);
                        if (annoName.length() == 0) {
                            continue;
                        }
                        if (REGEX_SPECIFIER.matcher(annoName).matches()) {
                            annoName = annoName.substring(1, annoName.length() - 1);
                            try {
                                if (extAnnoName.matches(annoName)) {
                                    mv = instrumentorFor(om, mv, lvs, tsIndex, access, name, desc);
                                }
                            } catch (PatternSyntaxException pse) {
                                reportPatternSyntaxException(extAnnoName);
                            }
                        } else if (annoName.equals(extAnnoName)) {
                            mv = instrumentorFor(om, mv, lvs, tsIndex, access, name, desc);
                        }
                    }
                }
                return mv.visitAnnotation(annoDesc, visible);
            }
        };
    }

    /**
     * 
     * @param om
     * @param mv 
     * @param lvs 本地变量排序器
     * @param tsIndex
     * @param access 方法访问控制符
     * @param name 方法名称
     * @param desc 描述符
     * @return
     */
    private MethodVisitor instrumentorFor(
        final OnMethod om, MethodVisitor mv, final LocalVariablesSorter lvs,
        final int[] tsIndex, int access, String name, final String desc) {
        //获取触探的位置
        final Location loc = om.getLocation();
        //触探的时机
        final Where where = loc.getWhere();
        //类型参数数组，从方法描述符中获取
        final Type[] actionArgTypes = Type.getArgumentTypes(om.getTargetDescriptor());
        //脚本参数个数
        final int numActionArgs = actionArgTypes.length;
        //处理触探位置类型值
        switch (loc.getValue()) {
            case ARRAY_GET:
                // <editor-fold defaultstate="collapsed" desc="Array Get Instrumentor">
                //获取数组时~返回一个数组访问器，并且在脚本方法参数中传入数组访问的索引和类型
                return new ArrayAccessInstrumentor(mv, className, superName, access, name, desc) {
                    //参数索引
                    int[] argsIndex = new int[]{-1, -1};
                    final private int INSTANCE_PTR = 0;
                    final private int INDEX_PTR = 1;

                    @Override
                    protected void onBeforeArrayLoad(int opcode) {
                        //通过opcode获取数组类型
                        Type arrtype = TypeUtils.getArrayType(opcode);
                        //获取数组元素类型
                        Type retType = TypeUtils.getElementType(opcode);
                        //@Self信息
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        //@Return信息
                        if (where == Where.AFTER) {
                            addExtraTypeInfo(om.getReturnParameter(), retType);
                        }
                        //参数验证结果，
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{arrtype, Type.INT_TYPE});
                        if (vr.isValid()) {
                            lvs.freeze();
                            try {
                                //不是AnyType
                                if (!vr.isAny()) {
                                    //复制栈顶一个long或者是double的数据并将复制的值也压入到栈顶
                                    dup2();
                                    //数组索引下标值变量
                                    argsIndex[INDEX_PTR] = lvs.newLocal(Type.INT_TYPE);
                                    //数组中实例类型，
                                    argsIndex[INSTANCE_PTR] = lvs.newLocal(arrtype);
                                }
                                if (where == Where.BEFORE) {
                                    //创建以下本地变量或常量，推到栈顶
                                    loadArguments(
                                        //数组下标
                                        new LocalVarArgProvider(vr.getArgIdx(INDEX_PTR), Type.INT_TYPE, argsIndex[INDEX_PTR]),
                                        //数组下标对应实例
                                        new LocalVarArgProvider(vr.getArgIdx(INSTANCE_PTR), arrtype, argsIndex[INSTANCE_PTR]),
                                        //@ProbeClassName参数
                                        new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                        //@ProbeMethodName参数
                                        new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                        //@Self参数
                                        new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));
                                    //添加调用指令和生成script方法
                                    invokeBTraceAction(this, om);
                                }
                            } finally {
                                lvs.unfreeze();
                            }
                        }
                    }

                    @Override
                    protected void onAfterArrayLoad(int opcode) {
                        if (where == Where.AFTER) {
                            Type arrtype = TypeUtils.getArrayType(opcode);
                            Type retType = TypeUtils.getElementType(opcode);
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            addExtraTypeInfo(om.getReturnParameter(), retType);
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{arrtype, Type.INT_TYPE});
                            if (vr.isValid()) {
                                lvs.freeze();
                                try {
                                    int retValIndex = -1;
                                    if (om.getReturnParameter() != -1) {
                                        dupArrayValue(opcode);
                                        retValIndex = lvs.newLocal(retType);
                                    }
                                    //如果有返回值，增加返回值
                                    loadArguments(
                                        new LocalVarArgProvider(vr.getArgIdx(INDEX_PTR), Type.INT_TYPE, argsIndex[INDEX_PTR]),
                                        new LocalVarArgProvider(vr.getArgIdx(INSTANCE_PTR), arrtype, argsIndex[INSTANCE_PTR]),
                                        new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                        new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                        new LocalVarArgProvider(om.getReturnParameter(), retType, retValIndex),
                                        new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));
                                    invokeBTraceAction(this, om);
                                } finally {
                                    lvs.unfreeze();
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case ARRAY_SET:
                // <editor-fold defaultstate="collapsed" desc="Array Set Instrumentor">
                return new ArrayAccessInstrumentor(mv, className, superName, access, name, desc) {
                    int[] argsIndex = new int[]{-1, -1, -1};
                    final private int INSTANCE_PTR = 0, INDEX_PTR = 1, VALUE_PTR = 2;

                    @Override
                    protected void onBeforeArrayStore(int opcode) {
                        Type elementType = TypeUtils.getElementType(opcode);
                        Type arrayType = TypeUtils.getArrayType(opcode);

                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{arrayType, Type.INT_TYPE, elementType});
                        if (vr.isValid()) {
                            lvs.freeze();
                            try {
                                if (!vr.isAny()) {
                                    argsIndex[VALUE_PTR] = lvs.newLocal(elementType);
                                    dup2();
                                    argsIndex[INDEX_PTR] = lvs.newLocal(Type.INT_TYPE);
                                    argsIndex[INSTANCE_PTR] = lvs.newLocal(TypeUtils.getArrayType(opcode));
                                    loadLocal(elementType, argsIndex[VALUE_PTR]);
                                }

                                if (where == Where.BEFORE) {
                                    loadArguments(
                                        new LocalVarArgProvider(vr.getArgIdx(INSTANCE_PTR), arrayType, argsIndex[INSTANCE_PTR]),
                                        new LocalVarArgProvider(vr.getArgIdx(INDEX_PTR), Type.INT_TYPE, argsIndex[INDEX_PTR]),
                                        new LocalVarArgProvider(vr.getArgIdx(VALUE_PTR), elementType, argsIndex[VALUE_PTR]),
                                        new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                        new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                        new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(this, om);
                                }
                            } finally {
                                lvs.unfreeze();
                            }
                        }
                    }

                    @Override
                    protected void onAfterArrayStore(int opcode) {
                        if (where == Where.AFTER) {
                            Type elementType = TypeUtils.getElementType(opcode);
                            Type arrayType = TypeUtils.getArrayType(opcode);

                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{arrayType, Type.INT_TYPE, elementType});
                            if (vr.isValid()) {
                                lvs.freeze();
                                try {
                                    loadArguments(
                                        new LocalVarArgProvider(vr.getArgIdx(INSTANCE_PTR), arrayType, argsIndex[INSTANCE_PTR]),
                                        new LocalVarArgProvider(vr.getArgIdx(INDEX_PTR), Type.INT_TYPE, argsIndex[INDEX_PTR]),
                                        new LocalVarArgProvider(vr.getArgIdx(VALUE_PTR), elementType, argsIndex[VALUE_PTR]),
                                        new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                        new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                        new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(this, om);
                                } finally {
                                    lvs.unfreeze();
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case CALL:
                // <editor-fold defaultstate="collapsed" desc="Method Call Instrumentor">
                //执行方法调用时
                return new MethodCallInstrumentor(mv, className, superName, access, name, desc) {
                    //被调用方法的类名
                    private String localClassName = loc.getClazz();
                    //被调用的方法名称
                    private String localMethodName = loc.getMethod();
                    //返回值
                    private int returnVarIndex = -1;
                    int[] backupArgsIndexes;

                    private void injectBtrace(ValidationResult vr, final String method, final Type[] callArgTypes, final Type returnType) {
                        ArgumentProvider[] actionArgs = new ArgumentProvider[actionArgTypes.length + 6];
                        for(int i=0;i<vr.getArgCnt();i++) {
                            int index = vr.getArgIdx(i);
                            Type t = actionArgTypes[index];
                            if (TypeUtils.isAnyTypeArray(t)) {
                                if (i < backupArgsIndexes.length - 1) {
                                    actionArgs[i] = new AnyTypeArgProvider(index, backupArgsIndexes[i+1], callArgTypes);
                                } else {
                                    actionArgs[i] = new ArgumentProvider(index) {

                                        @Override
                                        protected void doProvide() {
                                            push(0);
                                            visitTypeInsn(ANEWARRAY, TypeUtils.objectType.getInternalName());
                                        }
                                    };
                                }
                            } else {
                                actionArgs[i] = new LocalVarArgProvider(index, actionArgTypes[index], backupArgsIndexes[i+1]);;
                            }

                        }
                        actionArgs[actionArgTypes.length] = new LocalVarArgProvider(om.getReturnParameter(), returnType, returnVarIndex);
                        actionArgs[actionArgTypes.length + 1] = new LocalVarArgProvider(om.getTargetInstanceParameter(), TypeUtils.objectType, backupArgsIndexes[0]);
                        actionArgs[actionArgTypes.length + 2] = new ConstantArgProvider(om.getTargetMethodOrFieldParameter(), method);
                        actionArgs[actionArgTypes.length + 3] = new ConstantArgProvider(om.getClassNameParameter(), className);
                        actionArgs[actionArgTypes.length + 4] = new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn()));
                        actionArgs[actionArgTypes.length + 5] = new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0);

                        loadArguments(actionArgs);

                        invokeBTraceAction(this, om);
                    }

                    @Override
                    protected void onBeforeCallMethod(int opcode, String owner, String name, String desc) {
                        //静态方法没有this
                        if (isStatic() && om.getSelfParameter() > -1) {
                            return; // invalid combination; a static method can not provide *this*
                        }
                        if (matches(localClassName, owner.replace('/', '.'))
                                && matches(localMethodName, name)
                                && typeMatches(loc.getType(), desc)) {
                            //类名、方法名称、类型匹配成功
                            String method = (om.isTargetMethodOrFieldFqn() ? (owner + ".") : "") + name + (om.isTargetMethodOrFieldFqn() ? desc : "");
                            Type[] calledMethodArgs = Type.getArgumentTypes(desc);
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            if (where == Where.AFTER) {
                                addExtraTypeInfo(om.getReturnParameter(), Type.getReturnType(desc));
                            }
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, calledMethodArgs);
                            if (vr.isValid()) {
                                lvs.freeze();
                                try {
                                    boolean isStaticCall = (opcode == INVOKESTATIC);
                                    if (isStaticCall) {
                                        if (om.getTargetInstanceParameter() != -1) {
                                            return;
                                        }
                                    } else {
                                        if (where == Where.BEFORE && name.equals(CONSTRUCTOR)) {
                                            return;
                                        }
                                    }
                                    // will store the call args into local variables
                                    //保存被调用方法的参数到局部变量中
                                    backupArgsIndexes = backupStack(lvs, Type.getArgumentTypes(desc), isStaticCall);
                                    if (where == Where.BEFORE) {
                                        //在调用之前注入
                                        injectBtrace(vr, method, Type.getArgumentTypes(desc), Type.getReturnType(desc));
                                    }
                                    // put the call args back on stack so the method call can find them
                                    restoreStack(backupArgsIndexes, Type.getArgumentTypes(desc), isStaticCall);
                                } finally {
                                    lvs.unfreeze();
                                }
                            }
                        }
                    }

                    @Override
                    protected void onAfterCallMethod(int opcode,
                            String owner, String name, String desc) {
                        if (isStatic() && om.getSelfParameter() != -1) {
                            return;
                        }
                        if (where == Where.AFTER
                                && matches(localClassName, owner.replace('/', '.'))
                                && matches(localMethodName, name)
                                && typeMatches(loc.getType(), desc)) {
                            Type returnType = Type.getReturnType(desc);
                            Type[] calledMethodArgs = Type.getArgumentTypes(desc);
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            addExtraTypeInfo(om.getReturnParameter(), returnType);
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, calledMethodArgs);
                            if (vr.isValid()) {
                                lvs.freeze();
                                try {
                                    String method = name + desc;
                                    boolean withReturn = om.getReturnParameter() != -1 && returnType != Type.VOID_TYPE;
                                    if (withReturn) {
                                        // store the return value to a local variable
                                        int index = lvs.newLocal(returnType);
                                        returnVarIndex = index;
                                    }
                                    // will also retrieve the call args and the return value from the backup variables
                                    injectBtrace(vr, method, calledMethodArgs, returnType);
                                    if (withReturn) {
                                        loadLocal(returnType, returnVarIndex); // restore the return value
                                    }
                                } finally {
                                    lvs.unfreeze();
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case CATCH:
                // <editor-fold defaultstate="collapsed" desc="Catch Instrumentor">
                //抛出异常时
                return new CatchInstrumentor(mv, className, superName, access, name, desc) {

                    @Override
                    protected void onCatch(String type) {
                        //异常类型
                        Type exctype = Type.getObjectType(type);
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{exctype});
                        if (vr.isValid()) {
                            int index = -1;
                            lvs.freeze();
                            try {
                                if (!vr.isAny()) {
                                    //栈顶复制
                                    dup();
                                    //创建一个异常类型局部变量表索引
                                    index = lvs.newLocal(exctype);
                                }
                                loadArguments(
                                    new LocalVarArgProvider(vr.getArgIdx(0), exctype, index),
                                    new ConstantArgProvider(om.getClassNameParameter(), className),
                                    new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                    new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                invokeBTraceAction(this, om);
                            } finally {
                                lvs.unfreeze();
                            }
                        }
                    }
                };// </editor-fold>

            case CHECKCAST:
                // <editor-fold defaultstate="collapsed" desc="CheckCast Instrumentor">
                //执行类型检查时，instanceof
                return new TypeCheckInstrumentor(mv, className, superName, access, name, desc) {

                    private void callAction(int opcode, String desc) {
                        if (opcode == Opcodes.CHECKCAST) {
                            // TODO not really usefull
                            // It would be better to check for the original and desired type
                            Type castType = Type.getObjectType(desc);
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{castType});
                            if (vr.isValid()) {
                                lvs.freeze();
                                try {
                                    int castTypeIndex = -1;
                                    if (!vr.isAny()) {
                                        dup();
                                        castTypeIndex = lvs.newLocal(castType);
                                    }
                                    loadArguments(
                                        new LocalVarArgProvider(vr.getArgIdx(0), castType, castTypeIndex),
                                        new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                        new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                        new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(this, om);
                                } finally {
                                    lvs.unfreeze();
                                }
                            }
                        }
                    }

                    @Override
                    protected void onBeforeTypeCheck(int opcode, String desc) {
                        if (where == Where.BEFORE) {
                            callAction(opcode, desc);
                        }
                    }

                    @Override
                    protected void onAfterTypeCheck(int opcode, String desc) {
                        if (where == Where.AFTER) {
                            callAction(opcode, desc);
                        }
                    }
                };// </editor-fold>

            case ENTRY:
                // <editor-fold defaultstate="collapsed" desc="Method Entry Instrumentor">
                return new MethodEntryInstrumentor(mv, className, superName, access, name, desc) {
                    private void injectBtrace(ValidationResult vr) {
                        lvs.freeze();
                        try {
                            ArgumentProvider[] actionArgs = new ArgumentProvider[actionArgTypes.length + 3];
                            int ptr = isStatic() ? 0 : 1;
                            for(int i=0;i<vr.getArgCnt();i++) {
                                int index = vr.getArgIdx(i);
                                Type t = actionArgTypes[index];
                                if (TypeUtils.isAnyTypeArray(t)) {
                                    actionArgs[i] = new AnyTypeArgProvider(index, ptr);
                                    ptr++;
                                } else {
                                    actionArgs[i] = new LocalVarArgProvider(index, t, ptr);
                                    ptr += actionArgTypes[index].getSize();
                                }
                            }
                            actionArgs[actionArgTypes.length] = new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn()));
                            actionArgs[actionArgTypes.length + 1] = new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", "."));
                            actionArgs[actionArgTypes.length + 2] = new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0);
                            loadArguments(actionArgs);

                            invokeBTraceAction(this, om);
                        } finally {
                            lvs.unfreeze();
                        }
                    }

                    private void callAction() {
                        if (isStatic() && om.getSelfParameter() > -1) {
                            return; // invalid combination; a static method can not provide *this*
                        }
                        Type[] calledMethodArgs = Type.getArgumentTypes(getDescriptor());
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, calledMethodArgs);
                        if (vr.isValid()) {
                            injectBtrace(vr);
                        }
                    }

                    @Override
                    protected void onMethodEntry() {
                        if (numActionArgs == 0) {
                            invokeBTraceAction(this, om);
                        } else {
                            callAction();
                        }
                    }
                };// </editor-fold>

            case ERROR:
                // <editor-fold defaultstate="collapsed" desc="Error Instrumentor">
                ErrorReturnInstrumentor eri = new ErrorReturnInstrumentor(mv, tsIndex, className, superName, access, name, desc) {
                    ValidationResult vr;
                    {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.throwableType});
                    }

                    @Override
                    protected void onErrorReturn() {
                        if (vr.isValid()) {
                            int throwableIndex = -1;
                            lvs.freeze();
                            try {
                                if (om.getDurationParameter() != -1) {
                                    usesTimeStamp = true;
                                    // TODO: this is a nasty hack; should be in TimeStampGenerator but can't fit it there, no way :(
                                    if (tsIndex[1] == -1) {
                                        TimeStampHelper.generateTimeStampAccess(this, className);
                                        tsIndex[1] = lvs.newLocal(Type.LONG_TYPE);
                                    }
                                }
                                if (!vr.isAny()) {
                                    dup();
                                    throwableIndex = lvs.newLocal(TypeUtils.throwableType);
                                }

                                ArgumentProvider[] actionArgs = new ArgumentProvider[5];

                                actionArgs[0] = new LocalVarArgProvider(vr.getArgIdx(0), TypeUtils.throwableType, throwableIndex);
                                actionArgs[1] = new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", "."));
                                actionArgs[2] = new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn()));
                                actionArgs[3] = new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0);
                                actionArgs[4] = new ArgumentProvider(om.getDurationParameter()) {
                                    public void doProvide() {
                                        if (tsIndex[0] != -1 && tsIndex[1] != -1) {
                                            loadLocal(Type.LONG_TYPE, tsIndex[1]);
                                            loadLocal(Type.LONG_TYPE, tsIndex[0]);
                                            visitInsn(LSUB);
                                        }
                                    }
                                };

                                loadArguments(actionArgs);

                                invokeBTraceAction(this, om);
                            } finally {
                                lvs.unfreeze();
                            }
                        }
                    }

                    @Override
                    public boolean usesTimeStamp() {
                        return vr.isValid() && om.getDurationParameter() != -1;
                    }
                };
                if (om.getDurationParameter() != -1) {
                    return new TimeStampGenerator(lvs, tsIndex, className, superName, access, name, desc, eri, new int[0]);
                } else {
                    return eri;
                }// </editor-fold>

            case FIELD_GET:
                // <editor-fold defaultstate="collapsed" desc="Field Get Instrumentor">
                return new FieldAccessInstrumentor(mv, className, superName, access, name, desc) {

                    int calledInstanceIndex = -1;
                    private String targetClassName = loc.getClazz();
                    private String targetFieldName = (om.isTargetMethodOrFieldFqn() ? targetClassName + "." : "") + loc.getField();

                    @Override
                    protected void onBeforeGetField(int opcode, String owner,
                            String name, String desc) {
                        if (om.getTargetInstanceParameter() != -1 && isStaticAccess) {
                            return;
                        }
                        if (matches(targetClassName, owner.replace('/', '.'))
                                && matches(targetFieldName, name)) {

                            Type fldType = Type.getType(desc);
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            if (where == Where.AFTER) {
                                addExtraTypeInfo(om.getReturnParameter(), fldType);
                            }
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[0]);
                            if (vr.isValid()) {
                                lvs.freeze();
                                try {
                                    if (om.getTargetInstanceParameter() != -1) {
                                        dup();
                                        calledInstanceIndex = lvs.newLocal(TypeUtils.objectType);
                                    }
                                    if (where == Where.BEFORE) {
                                        loadArguments(
                                            new LocalVarArgProvider(om.getTargetInstanceParameter(), TypeUtils.objectType, calledInstanceIndex),
                                            new ConstantArgProvider(om.getTargetMethodOrFieldParameter(), targetFieldName),
                                            new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                            new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                            new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                        invokeBTraceAction(this, om);
                                    }
                                } finally {
                                    lvs.unfreeze();
                                }
                            }
                        }
                    }

                    @Override
                    protected void onAfterGetField(int opcode, String owner,
                            String name, String desc) {
                        if (om.getTargetInstanceParameter() != -1 && isStaticAccess) {
                            return;
                        }
                        if (where == Where.AFTER
                                && matches(targetClassName, owner.replace('/', '.'))
                                && matches(targetFieldName, name)) {
                            Type fldType = Type.getType(desc);

                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            addExtraTypeInfo(om.getReturnParameter(), fldType);
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[0]);
                            if (vr.isValid()) {
                                int returnValIndex = -1;
                                lvs.freeze();
                                try {
                                    if (om.getReturnParameter() != -1) {
                                        dupValue(desc);
                                        returnValIndex = lvs.newLocal(fldType);
                                    }

                                    loadArguments(
                                        new LocalVarArgProvider(om.getTargetInstanceParameter(), TypeUtils.objectType, calledInstanceIndex),
                                        new ConstantArgProvider(om.getTargetMethodOrFieldParameter(), targetFieldName),
                                        new LocalVarArgProvider(om.getReturnParameter(), fldType, returnValIndex),
                                        new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                        new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                        new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(this, om);
                                } finally {
                                    lvs.unfreeze();
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case FIELD_SET:
                // <editor-fold defaultstate="collapsed" desc="Field Set Instrumentor">
                return new FieldAccessInstrumentor(mv, className, superName, access, name, desc) {
                    private String targetClassName = loc.getClazz();
                    private String targetFieldName = (om.isTargetMethodOrFieldFqn() ? targetClassName + "." : "") + loc.getField();
                    private int calledInstanceIndex = -1;
                    private int fldValueIndex = -1;

                    @Override
                    protected void onBeforePutField(int opcode, String owner,
                            String name, String desc) {
                        if (om.getTargetInstanceParameter() != -1 && isStaticAccess) {
                            return;
                        }
                        if (matches(targetClassName, owner.replace('/', '.'))
                                && matches(targetFieldName, name)) {

                            Type fieldType = Type.getType(desc);

                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{fieldType});

                            if (vr.isValid()) {
                                lvs.freeze();
                                try {
                                    if (!vr.isAny()) {
                                        fldValueIndex = lvs.newLocal(fieldType);
                                    }
                                    if (om.getTargetInstanceParameter() != -1) {
                                        dup();
                                        calledInstanceIndex = lvs.newLocal(TypeUtils.objectType);
                                    }
                                    if (!vr.isAny()) {
                                        // need to put the set value back on stack
                                        loadLocal(fieldType, fldValueIndex);
                                    }

                                    if (where == Where.BEFORE) {
                                        loadArguments(
                                            new LocalVarArgProvider(vr.getArgIdx(0), fieldType, fldValueIndex),
                                            new LocalVarArgProvider(om.getTargetInstanceParameter(), TypeUtils.objectType, calledInstanceIndex),
                                            new ConstantArgProvider(om.getTargetMethodOrFieldParameter(), targetFieldName),
                                            new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                            new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                            new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                        invokeBTraceAction(this, om);
                                    }
                                } finally {
                                    lvs.unfreeze();
                                }
                            }
                        }
                    }

                    @Override
                    protected void onAfterPutField(int opcode,
                            String owner, String name, String desc) {
                        if (om.getTargetInstanceParameter() != -1 && isStaticAccess) {
                            return;

                        }
                        if (where == Where.AFTER
                                && matches(targetClassName, owner.replace('/', '.'))
                                && matches(targetFieldName, name)) {
                            Type fieldType = Type.getType(desc);

                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{fieldType});

                            if (vr.isValid()) {
                                lvs.freeze();
                                try {
                                    loadArguments(
                                            new LocalVarArgProvider(vr.getArgIdx(0), fieldType, fldValueIndex),
                                            new LocalVarArgProvider(om.getTargetInstanceParameter(), TypeUtils.objectType, calledInstanceIndex),
                                            new ConstantArgProvider(om.getTargetMethodOrFieldParameter(), targetFieldName),
                                            new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                            new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                            new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(this, om);
                                } finally {
                                    lvs.unfreeze();
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case INSTANCEOF:
                // <editor-fold defaultstate="collapsed" desc="InstanceOf Instrumentor">
                return new TypeCheckInstrumentor(mv, className, superName, access, name, desc) {

                    private void callAction(int opcode, String desc) {
                        if (opcode == Opcodes.INSTANCEOF) {
                            // TODO not really usefull
                            // It would be better to check for the original and desired type
                            Type castType = Type.getObjectType(desc);
                            addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                            ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{castType});
                            if (vr.isValid()) {
                                int castTypeIndex = -1;
                                lvs.freeze();
                                try {
                                    if (!vr.isAny()) {
                                        dup();
                                        castTypeIndex = lvs.newLocal(castType);
                                    }

                                    loadArguments(
                                        new LocalVarArgProvider(vr.getArgIdx(0), castType, castTypeIndex),
                                        new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                        new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                        new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                    invokeBTraceAction(this, om);
                                } finally {
                                    lvs.unfreeze();
                                }
                            }
                        }
                    }

                    @Override
                    protected void onBeforeTypeCheck(int opcode, String desc) {
                        if (where == Where.BEFORE) {
                            callAction(opcode, desc);
                        }
                    }

                    @Override
                    protected void onAfterTypeCheck(int opcode, String desc) {
                        if (where == Where.AFTER) {
                            callAction(opcode, desc);
                        }
                    }
                };// </editor-fold>

            case LINE:
                // <editor-fold defaultstate="collapsed" desc="Line Instrumentor">
                return new LineNumberInstrumentor(mv, className, superName, access, name, desc) {

                    private int onLine = loc.getLine();

                    private void callOnLine(int line) {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{Type.INT_TYPE});
                        if (vr.isValid()) {
                            lvs.freeze();
                            try {
                                loadArguments(
                                    new ConstantArgProvider(vr.getArgIdx(0), line),
                                    new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                    new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                    new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                invokeBTraceAction(this, om);
                            } finally {
                                lvs.unfreeze();
                            }
                        }
                    }

                    @Override
                    protected void onBeforeLine(int line) {
                        if ((line == onLine || onLine == -1)
                                && where == Where.BEFORE) {
                            callOnLine(line);
                        }
                    }

                    @Override
                    protected void onAfterLine(int line) {
                        if ((line == onLine || onLine == -1)
                                && where == Where.AFTER) {
                            callOnLine(line);
                        }
                    }
                };// </editor-fold>

            case NEW:
                // <editor-fold defaultstate="collapsed" desc="New Instance Instrumentor">
                return new ObjectAllocInstrumentor(mv, className, superName, access, name, desc) {

                    @Override
                    protected void beforeObjectNew(String desc) {
                        if (loc.getWhere() == Where.BEFORE) {
                            //对象名称
                            String extName = desc.replace('/', '.');
                            //指定触探的对象匹配的话
                            if (matches(loc.getClazz(), extName)) {
                                //@Self参数
                                addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                                //脚本方法参数验证
                                ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.stringType});
                                if (vr.isValid()) {//合法
                                    //冻结本地变量
                                    lvs.freeze();
                                    try {
                                        //注入3个常量和本地self变量
                                        loadArguments(
                                            new ConstantArgProvider(vr.getArgIdx(0), extName),
                                            new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                            new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                            new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));
                                        //调用脚本方法把脚本方法复制到当前类中
                                        invokeBTraceAction(this, om);
                                    } finally {
                                        lvs.unfreeze();
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    protected void afterObjectNew(String desc) {
                        if (loc.getWhere() == Where.AFTER) {
                            String extName = desc.replace('/', '.');
                            if (matches(loc.getClazz(), extName)) {
                                Type instType = Type.getObjectType(desc);

                                addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                                addExtraTypeInfo(om.getReturnParameter(), instType);
                                ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.stringType});
                                if (vr.isValid()) {
                                    int returnValIndex = -1;
                                    lvs.freeze();
                                    try {
                                        if (om.getReturnParameter() != -1) {
                                            dupValue(instType);
                                            returnValIndex = lvs.newLocal(instType);
                                        }
                                        loadArguments(
                                            new ConstantArgProvider(vr.getArgIdx(0), extName),
                                            new LocalVarArgProvider(om.getReturnParameter(), instType, returnValIndex),
                                            new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                            new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                            new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                        invokeBTraceAction(this, om);
                                    } finally {
                                        lvs.unfreeze();
                                    }
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case NEWARRAY:
                // <editor-fold defaultstate="collapsed" desc="New Array Instrumentor">
                return new ArrayAllocInstrumentor(mv, className, superName, access, name, desc) {

                    @Override
                    protected void onBeforeArrayNew(String desc, int dims) {
                        if (where == Where.BEFORE) {
                            String extName = TypeUtils.getJavaType(desc);
                            String type = TypeUtils.objectOrArrayType(loc.getClazz());
                            if (matches(type, desc)) {
                                addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                                ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.stringType, Type.INT_TYPE});
                                if (vr.isValid()) {
                                    lvs.freeze();
                                    try {
                                        loadArguments(
                                            new ConstantArgProvider(vr.getArgIdx(0), extName),
                                            new ConstantArgProvider(vr.getArgIdx(1), dims),
                                            new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                            new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                            new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                        invokeBTraceAction(this, om);
                                    } finally {
                                        lvs.unfreeze();
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    protected void onAfterArrayNew(String desc, int dims) {
                        if (where == Where.AFTER) {
                            String extName = TypeUtils.getJavaType(desc);
                            String type = TypeUtils.objectOrArrayType(loc.getClazz());
                            if (matches(type, desc)) {
                                StringBuilder arrayType = new StringBuilder();
                                for (int i = 0; i < dims; i++) {
                                    arrayType.append("[");
                                }
                                arrayType.append(desc);
                                Type instType = Type.getObjectType(arrayType.toString());
                                addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                                addExtraTypeInfo(om.getReturnParameter(), instType);
                                ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.stringType, Type.INT_TYPE});
                                if (vr.isValid()) {
                                    int returnValIndex = -1;
                                    lvs.freeze();
                                    try {
                                        if (om.getReturnParameter() != -1) {
                                            dupValue(instType);
                                            returnValIndex = lvs.newLocal(instType);
                                        }
                                        loadArguments(
                                            new ConstantArgProvider(vr.getArgIdx(0), extName),
                                            new ConstantArgProvider(vr.getArgIdx(1), dims),
                                            new LocalVarArgProvider(om.getReturnParameter(), instType, returnValIndex),
                                            new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                            new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                            new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                        invokeBTraceAction(this, om);
                                    } finally {
                                        lvs.unfreeze();
                                    }
                                }
                            }
                        }
                    }
                };// </editor-fold>

            case RETURN:
                // <editor-fold defaultstate="collapsed" desc="Return Instrumentor">
                if (where != Where.BEFORE) {
                    return mv;
                }
                MethodReturnInstrumentor mri = new MethodReturnInstrumentor(mv, tsIndex, className, superName, access, name, desc) {

                    int retValIndex;
                    ValidationResult vr;
                    {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        addExtraTypeInfo(om.getReturnParameter(), getReturnType());

                        vr = validateArguments(om, isStatic(), actionArgTypes, Type.getArgumentTypes(getDescriptor()));
                    }

                    private void callAction(int retOpCode) {
                        if (!vr.isValid()) {
                            return;

                        }
                        lvs.freeze();
                        try {
                            if (om.getReturnParameter() != -1) {
                                dupReturnValue(retOpCode);
                                retValIndex = lvs.newLocal(getReturnType());
                            }
                            if (om.getDurationParameter() != -1) {
                                usesTimeStamp = true;
                            }

                            ArgumentProvider[] actionArgs = new ArgumentProvider[actionArgTypes.length + 4 + (tsIndex !=null ? 1 : 0)];
                            int ptr = isStatic() ? 0 : 1;
                            for(int i=0;i<vr.getArgCnt();i++) {
                                int index = vr.getArgIdx(i);
                                Type t = actionArgTypes[index];
                                if (TypeUtils.isAnyTypeArray(t)) {
                                    actionArgs[i] = new AnyTypeArgProvider(i, ptr);
                                    ptr++;
                                } else {
                                    actionArgs[i] = new LocalVarArgProvider(index, t, ptr);
                                    ptr += actionArgTypes[index].getSize();
                                }
                            }
                            actionArgs[actionArgTypes.length] = new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn()));
                            actionArgs[actionArgTypes.length + 1] = new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", "."));
                            actionArgs[actionArgTypes.length + 2] = new LocalVarArgProvider(om.getReturnParameter(), getReturnType(), retValIndex);
                            actionArgs[actionArgTypes.length + 3] = new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0);
                            actionArgs[actionArgTypes.length + 4] = new ArgumentProvider(om.getDurationParameter()) {
                                public void doProvide() {
                                    if (tsIndex[0] != -1 && tsIndex[1] != -1) {
                                        loadLocal(Type.LONG_TYPE, tsIndex[1]);
                                        loadLocal(Type.LONG_TYPE, tsIndex[0]);
                                        visitInsn(LSUB);
                                    }
                                }
                            };
                            loadArguments(actionArgs);

                            invokeBTraceAction(this, om);
                        } finally {
                            lvs.unfreeze();
                        }
                    }

                    @Override
                    protected void onMethodReturn(int opcode) {
                        if (numActionArgs == 0) {
                            invokeBTraceAction(this, om);
                        } else {
                            callAction(opcode);
                        }
                    }

                    @Override
                    public boolean usesTimeStamp() {
                        return vr.isValid() && om.getDurationParameter() != -1;
                    }
                };
                if (om.getDurationParameter() != -1) {
                    return new TimeStampGenerator(lvs, tsIndex, className, superName, access, name, desc, mri, new int[]{RETURN, IRETURN, FRETURN, DRETURN, LRETURN, ARETURN});
                } else {
                    return mri;
                }// </editor-fold>

            case SYNC_ENTRY:
                // <editor-fold defaultstate="collapsed" desc="SyncEntry Instrumentor">
                return new SynchronizedInstrumentor(mv, tsIndex, className, superName, access, name, desc) {

                    private void callAction() {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.objectType});
                        if (vr.isValid()) {
                            int index = -1;
                            lvs.freeze();
                            try {
                                if (!vr.isAny()) {
                                    dup();
                                    index = lvs.newLocal(TypeUtils.objectType);
                                }
                                loadArguments(
                                    new LocalVarArgProvider(vr.getArgIdx(0), TypeUtils.objectType, index),
                                    new ConstantArgProvider(om.getClassNameParameter(), className),
                                    new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                    new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));
                                invokeBTraceAction(this, om);
                            } finally {
                                lvs.unfreeze();
                            }
                        }
                    }

                    @Override
                    protected void onBeforeSyncEntry() {
                        if (where == Where.BEFORE) {
                            callAction();
                        }
                    }

                    @Override
                    protected void onAfterSyncEntry() {
                        if (where == Where.AFTER) {
                            callAction();
                        }
                    }

                    @Override
                    protected void onBeforeSyncExit() {
                    }

                    @Override
                    protected void onAfterSyncExit() {
                    }
                };// </editor-fold>

            case SYNC_EXIT:
                // <editor-fold defaultstate="collapsed" desc="SyncExit Instrumentor">
                return new SynchronizedInstrumentor(mv, tsIndex, className, superName, access, name, desc) {

                    private void callAction() {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.objectType});
                        if (vr.isValid()) {
                            int index = -1;
                            lvs.freeze();
                            try {
                                if (!vr.isAny()) {
                                    dup();
                                    index = lvs.newLocal(TypeUtils.objectType);
                                }
                                loadArguments(
                                    new LocalVarArgProvider(vr.getArgIdx(0), TypeUtils.objectType, index),
                                    new ConstantArgProvider(om.getClassNameParameter(), className),
                                    new ConstantArgProvider(om.getMethodParameter(), getName(om.isMethodFqn())),
                                    new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                invokeBTraceAction(this, om);
                            } finally {
                                lvs.unfreeze();
                            }
                        }
                    }

                    @Override
                    protected void onBeforeSyncEntry() {
                    }

                    @Override
                    protected void onAfterSyncEntry() {
                    }

                    @Override
                    protected void onBeforeSyncExit() {
                        if (where == Where.BEFORE) {
                            callAction();
                        }
                    }

                    @Override
                    protected void onAfterSyncExit() {
                        if (where == Where.AFTER) {
                            callAction();
                        }
                    }
                };// </editor-fold>

            case THROW:
                // <editor-fold defaultstate="collapsed" desc="Throw Instrumentor">
                return new ThrowInstrumentor(mv, className, superName, access, name, desc) {

                    @Override
                    protected void onThrow() {
                        addExtraTypeInfo(om.getSelfParameter(), Type.getObjectType(className));
                        ValidationResult vr = validateArguments(om, isStatic(), actionArgTypes, new Type[]{TypeUtils.throwableType});
                        if (vr.isValid()) {
                            int throwableIndex = -1;
                            lvs.freeze();
                            try {
                                if (!vr.isAny()) {
                                    dup();
                                    throwableIndex = lvs.newLocal(TypeUtils.throwableType);
                                }
                                loadArguments(
                                    new LocalVarArgProvider(vr.getArgIdx(0), TypeUtils.throwableType, throwableIndex),
                                    new ConstantArgProvider(om.getClassNameParameter(), className.replace("/", ".")),
                                    new ConstantArgProvider(om.getMethodParameter(),getName(om.isMethodFqn())),
                                    new LocalVarArgProvider(om.getSelfParameter(), Type.getObjectType(className), 0));

                                invokeBTraceAction(this, om);
                            } finally {
                                lvs.unfreeze();
                            }
                        }
                    }
                };// </editor-fold>
        }
        return mv;
    }

    private void introduceTimeStampHelper() {
        if (usesTimeStamp && !timeStampExisting) {
            TimeStampHelper.generateTimeStampGetter(this);
        }
    }
    //最后把脚本方法添加到转换换的类中
    public void visitEnd() {
        int size = applicableOnMethods.size();
        List<MethodCopier.MethodInfo> mi = new ArrayList<MethodCopier.MethodInfo>(size);
        for (OnMethod om : calledOnMethods) {
            //方法信息
            // private static $btrace$btraceClassName$methodName(){}
            mi.add(new MethodCopier.MethodInfo(om.getTargetName(),
                     om.getTargetDescriptor(),
                     getActionMethodName(om.getTargetName()),
                     ACC_STATIC | ACC_PRIVATE));
        }
        //System.nanoTime();
        introduceTimeStampHelper();
        //创建一个方法拷贝器
        MethodCopier copier = new MethodCopier(btraceClass, cv, mi) {
            @Override
            protected MethodVisitor addMethod(int access, String name, String desc,
                        String signature, String[] exceptions) {
                desc = desc.replace(ANYTYPE_DESC, OBJECT_DESC);
                if (signature != null) {
                    signature = signature.replace(ANYTYPE_DESC, OBJECT_DESC);
                }
                return super.addMethod(access, name, desc, signature, exceptions);
            }
        };
        copier.visitEnd();
    }


    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java com.sun.btrace.runtime.Instrumentor <btrace-class> <target-class>]");
            System.exit(1);
        }

        String className = args[0].replace('.', '/') + ".class";
        FileInputStream fis = new FileInputStream(className);
        byte[] buf = new byte[(int)new File(className).length()];
        fis.read(buf);
        fis.close();
        ClassWriter writer = InstrumentUtils.newClassWriter();
        Verifier verifier = new Verifier(new Preprocessor(writer));
        InstrumentUtils.accept(new ClassReader(buf), verifier);
        buf = writer.toByteArray();
        FileOutputStream fos = new FileOutputStream(className);
        fos.write(buf);
        fos.close();
        String targetClass = args[1].replace('.', '/') + ".class";
        fis = new FileInputStream(targetClass);
        writer = InstrumentUtils.newClassWriter();
        ClassReader reader = new ClassReader(fis);
        InstrumentUtils.accept(reader, new Instrumentor(null,
                    verifier.getClassName(), buf,
                    verifier.getOnMethods(), writer));
        fos = new FileOutputStream(targetClass);
        fos.write(writer.toByteArray());
    }

    private String getActionMethodName(String name) {
        return Constants.BTRACE_METHOD_PREFIX +
               btraceClassName.replace('/', '$') + "$" + name;
    }

    private void invokeBTraceAction(MethodInstrumentor mv, OnMethod om) {
        //调用脚本方法~,脚本方法已经copy到了被转换的类中
        mv.invokeStatic(className, getActionMethodName(om.getTargetName()),
            om.getTargetDescriptor().replace(ANYTYPE_DESC, OBJECT_DESC));
        calledOnMethods.add(om);
    }

    private boolean matches(String pattern, String input) {
        if (pattern.length() == 0) {
            return false;
        }
        if (pattern.charAt(0) == '/' &&
            REGEX_SPECIFIER.matcher(pattern).matches()) {
            try {
                return input.matches(pattern.substring(1, pattern.length() - 1));
            } catch (PatternSyntaxException pse) {
                reportPatternSyntaxException(pattern.substring(1, pattern.length() - 1));
                return false;
            }
        } else {
            return pattern.equals(input);
        }
    }

    private boolean typeMatches(String decl, String desc) {
        // empty type declaration matches any method signature
        if (decl.isEmpty()) {
            return true;
        } else {
            String d = TypeUtils.declarationToDescriptor(decl);
            Type[] args1 = Type.getArgumentTypes(d);
            Type[] args2 = Type.getArgumentTypes(desc);
            return TypeUtils.isCompatible(args1, args2);
        }
    }

    private static boolean isInArray(String[] candidates, String given) {
        for (String c : candidates) {
            if (c.equals(given)) {
                return true;
            }
        }
        return false;
    }

    private static void reportPatternSyntaxException(String pattern) {
        System.err.println("btrace ERROR: invalid regex pattern - " + pattern);
    }
}

