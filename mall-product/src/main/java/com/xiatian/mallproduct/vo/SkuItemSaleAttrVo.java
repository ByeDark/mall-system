package com.xiatian.mallproduct.vo;

import lombok.Data;

import java.util.List;

@Data
public class SkuItemSaleAttrVo{
    private  Long attrId;
    private String attrName;
    private List<String> attrValues;
}