package com.xiatian.mallproduct.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiatian.mallproduct.constant.ProductConstant;
import com.xiatian.mallproduct.entity.*;
import com.xiatian.mallproduct.feign.CouponFeignService;
import com.xiatian.mallproduct.feign.SearchFeignServiceUp;
import com.xiatian.mallproduct.feign.WareFeignService;
import com.xiatian.mallproduct.service.*;
import com.xiatian.mallproduct.mapper.SpuInfoMapper;
import com.xiatian.mallproduct.to.SkuEsModel;
import com.xiatian.mallproduct.to.SkuHasStockVo;
import com.xiatian.mallproduct.to.SkuReductionTo;
import com.xiatian.mallproduct.to.SpuBoundTo;
import com.xiatian.mallproduct.utils.PageUtils;
import com.xiatian.mallproduct.utils.Query;
import com.xiatian.mallproduct.utils.R;
import com.xiatian.mallproduct.vo.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
* @author XT189
* @description 针对表【pms_spu_info(spu信息)】的数据库操作Service实现
* @createDate 2023-11-07 15:02:23
*/
@Service
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoMapper, SpuInfo>
    implements SpuInfoService{

    @Autowired
    private SpuInfoDescService spuInfoDescService;

    @Autowired
    private SpuImagesService spuImagesService;

    @Autowired
    private AttrService attrService;

    @Autowired
    private ProductAttrValueService productAttrValueService;

    @Autowired
    private SkuInfoService skuInfoService;

    @Autowired
    private SkuImagesService skuImagesService;

    @Autowired
    private SkuSaleAttrValueService skuSaleAttrValueService;

    @Autowired
    private BrandService brandService;

    @Autowired
    CouponFeignService couponFeignService;

    @Autowired
    private CategoryService categoryService;
    @Autowired
    WareFeignService wareFeignService;
    @Autowired
    SearchFeignServiceUp searchFeignServiceUp;
    @Autowired
    ProductAttrValueService attrValueService;

    @Override
    public PageUtils queryPageByCondtion(Map<String, Object> params) {
        QueryWrapper<SpuInfo> queryWrapper = new QueryWrapper<>();
        String key = (String) params.get("key");
        if (StringUtils.hasText(key)) {
            queryWrapper.and((wrapper) -> {
                wrapper.eq("id",key).or().like("spu_name",key);
            });
        }
        String status = (String) params.get("status");
        if (StringUtils.hasText(status)) {
            queryWrapper.eq("publish_status",status);
        }
        String brandId = (String) params.get("brandId");
        if (StringUtils.hasText(brandId) && !"0".equalsIgnoreCase(brandId)) {
            queryWrapper.eq("brand_id",brandId);
        }
        String catelogId = (String) params.get("catelogId");
        if (StringUtils.hasText(catelogId) && !"0".equalsIgnoreCase(catelogId)) {
            queryWrapper.eq("catalog_id",catelogId);
        }
        IPage<SpuInfo> page = this.page(new Query<SpuInfo>().getPage(params), queryWrapper);
        return new PageUtils(page);
    }

    @Override
    public void saveBaseSpuInfo(SpuInfo spuInfoEntity) {
        this.baseMapper.insert(spuInfoEntity);
    }


    @Transactional
    @Override
    public void savesupInfo(SpuSaveVo vo) {
        //1、保存spu基本信息：pms_spu_info
        SpuInfo spuInfoEntity = new SpuInfo();
        BeanUtils.copyProperties(vo,spuInfoEntity);
        spuInfoEntity.setCreateTime(new Date());
        spuInfoEntity.setUpdateTime(new Date());
        this.saveBaseSpuInfo(spuInfoEntity);

        //2、保存spu的描述图片：pms_spu_info_desc
        List<String> decript = vo.getDecript();
        SpuInfoDesc spuInfoDescEntity = new SpuInfoDesc();
        spuInfoDescEntity.setSpuId(spuInfoEntity.getId());
        spuInfoDescEntity.setDecript(String.join(",",decript));
        spuInfoDescService.saveSpuInfoDesc(spuInfoDescEntity);

        //3、保存spu的图片集：pms_spu_images
        List<String> images = vo.getImages();
        spuImagesService.saveImages(spuInfoEntity.getId(),images);

        //4、保存spu的规格参数：pms_product_attr_value
        List<BaseAttrs> baseAttrs = vo.getBaseAttrs();
        List<ProductAttrValue> collect = baseAttrs.stream().map(attr -> {
            ProductAttrValue valueEntity = new ProductAttrValue();
            valueEntity.setAttrId(attr.getAttrId());

            //查询attr属性名
            Attr byId = attrService.getById(attr.getAttrId());

            valueEntity.setAttrName(byId.getAttrName());
            valueEntity.setAttrValue(attr.getAttrValues());
            valueEntity.setQuickShow(attr.getShowDesc());
            valueEntity.setSpuId(spuInfoEntity.getId());
            return valueEntity;
        }).collect(Collectors.toList());
        productAttrValueService.saveProductAttr(collect);

        //5、保存spu的积分信息：gulimall_sms--->sms_spu_bounds
        Bounds bounds = vo.getBounds();
        SpuBoundTo spuBoundTo = new SpuBoundTo();
        BeanUtils.copyProperties(bounds,spuBoundTo);
        spuBoundTo.setSpuId(spuInfoEntity.getId());
        R r = couponFeignService.saveSpuBounds(spuBoundTo);

        if (r.getCode() != 0) {
            log.error("远程保存spu积分信息失败");
        }

        //5、保存当前spu对应的所有sku信息：pms_sku_info
        //5、1）、sku的基本信息:pms_sku_info
        List<Skus> skus = vo.getSkus();
        if(skus!=null && skus.size()>0){
            skus.forEach(item->{
                String defaultImg = "";
                for (Images image : item.getImages()) {
                    if(image.getDefaultImg() == 1){
                        defaultImg = image.getImgUrl();
                    }
                }

                SkuInfo skuInfoEntity = new SkuInfo();
                BeanUtils.copyProperties(item,skuInfoEntity);
                skuInfoEntity.setBrandId(spuInfoEntity.getBrandId());
                skuInfoEntity.setCatalogId(spuInfoEntity.getCatalogId());
                skuInfoEntity.setSaleCount(0L);
                skuInfoEntity.setSpuId(spuInfoEntity.getId());
                skuInfoEntity.setSkuDefaultImg(defaultImg);
                skuInfoService.saveSkuInfo(skuInfoEntity);

                Long skuId = skuInfoEntity.getSkuId();

                List<SkuImages> imagesEntities = item.getImages().stream().map(img -> {
                    SkuImages skuImagesEntity = new SkuImages();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setImgUrl(img.getImgUrl());
                    skuImagesEntity.setDefaultImg(img.getDefaultImg());
                    return skuImagesEntity;
                }).filter(entity -> {
                    //返回true就是需要，false就是剔除
                    return StringUtils.hasText(entity.getImgUrl());
                }).collect(Collectors.toList());

                //5、2）、sku的图片信息：pms_sku_images
                skuImagesService.saveBatch(imagesEntities);

                //5、3）、sku的销售属性：pms_sku_sale_attr_value
                List<Attr> attr = item.getAttr();
                List<SkuSaleAttrValue> skuSaleAttrValueEntities = attr.stream().map(a -> {
                    SkuSaleAttrValue skuSaleAttrValueEntity = new SkuSaleAttrValue();
                    BeanUtils.copyProperties(a, skuSaleAttrValueEntity);
                    skuSaleAttrValueEntity.setSkuId(skuId);
                    return skuSaleAttrValueEntity;
                }).collect(Collectors.toList());

                skuSaleAttrValueService.saveBatch(skuSaleAttrValueEntities);

                //5、4）、sku的优惠、满减等信息：gulimall_sms--->sms_sku_ladder、sms_sku_full_reduction、sms_member_price
                SkuReductionTo skuReductionTo = new SkuReductionTo();
                BeanUtils.copyProperties(item,skuReductionTo);
                skuReductionTo.setSkuId(skuId);
                if (skuReductionTo.getFullCount() > 0 || skuReductionTo.getFullPrice().compareTo(BigDecimal.ZERO) == 1) {
                    R r1 = couponFeignService.saveSkuReduction(skuReductionTo);
                    if (r1.getCode() != 0) {
                        log.error("远程保存sku积分信息失败");
                    }
                }
            });
        }
    }

    /**
     * 根据skuId查询spu的信息
     * @param skuId
     * @return
     */
    @Override
    public SpuInfo getSpuInfoBySkuId(Long skuId) {

        //先查询sku表里的数据
        SkuInfo skuInfoEntity = skuInfoService.getById(skuId);

        //获得spuId
        Long spuId = skuInfoEntity.getSpuId();

        //再通过spuId查询spuInfo信息表里的数据
        SpuInfo spuInfo = this.baseMapper.selectById(spuId);

        //查询品牌表的数据获取品牌名
        Brand brandEntity = brandService.getById(spuInfo.getBrandId());
        //spuInfo.setBrandName(brandEntity.getName());

        return spuInfo;
    }


    //上架功能，默认添加了spu和sku的时候默认是不上架的
    @Override
    public void up(Long spuId) {
        // 1 组装数据 查出当前spuId对应的所有sku信息
        List<SkuInfo> skus = skuInfoService.getSkusBySpuId(spuId);
        List<Long> skuids = skus.stream().map(SkuInfo::getSkuId).collect(Collectors.toList());
        // 2 封装每个sku的信息
        // 3.查询当前sku所有可以被用来检索的规格属性
        // 获取所有的spu商品的id 然后查询这些id中那些是可以被检索的,获得spu属性，然后获得可以检索的属性id
        List<ProductAttrValue> baseAttrs = attrValueService.baseAttrListForSpu(spuId);
        List<Long> attrIds = baseAttrs.stream().map(ProductAttrValue::getAttrId).collect(Collectors.toList());
        List<Long> searchAttrIds = attrService.selectSearchAttrIds(attrIds);
        // 可检索的id集合
        Set<Long> isSet = new HashSet<>(searchAttrIds);
        // 根据商品id 过滤不可检索的商品 最后映射号检索属性
        List<SkuEsModel.Attrs> attrs = baseAttrs.stream().filter(item -> isSet.contains(item.getAttrId())).map(item -> {
            SkuEsModel.Attrs attr = new SkuEsModel.Attrs();
            BeanUtils.copyProperties(item, attr);
            return attr;
        }).collect(Collectors.toList());
        // skuId 对应 是否有库存
        Map<Long, Boolean> stockMap = null;
        try {
            // 3.1 发送远程调用 库存系统查询是否有库存
            R hasStock = wareFeignService.getSkusHasStock(skuids);
            // 构造器受保护 所以写成内部类对象
            stockMap = hasStock.getData(new TypeReference<List<SkuHasStockVo>>(){}).
                    stream().collect(Collectors.toMap(SkuHasStockVo::getSkuId, SkuHasStockVo::getHasStock));
            log.warn("服务调用成功" + hasStock);
        } catch (Exception e) {
            log.error("库存服务调用失败: 原因{}",e);
        }
        Map<Long, Boolean> finalStockMap = stockMap;
        List<SkuEsModel> collect = skus.stream().map(sku -> {
            SkuEsModel esModel = new SkuEsModel();
            BeanUtils.copyProperties(sku, esModel);
            esModel.setSkuPrice(sku.getPrice());
            esModel.setSkuImg(sku.getSkuDefaultImg());
            // 4 设置库存
            if(finalStockMap == null){
                esModel.setHasStock(true);
            }else {
                esModel.setHasStock(finalStockMap.get(sku.getSkuId()));
            }
            // TODO 1.热度评分 0
            esModel.setHotScore(0L);
            Brand brandEntity = brandService.getById(esModel.getBrandId());
            // brandName、brandImg
            esModel.setBrandName(brandEntity.getName());
            esModel.setBrandImg(brandEntity.getLogo());
            // 查询分类信息
            Category categoryEntity = categoryService.getById(esModel.getCatalogId());
            esModel.setCatalogName(categoryEntity.getName());
            // 保存商品的属性
            esModel.setAttrs(attrs);
            return esModel;
        }).collect(Collectors.toList());
        //发送给ES进行上架操作
        //使用search服务
        R r = searchFeignServiceUp.productStatusUp(collect);
        if(r.getCode()==0){
            //远程调用成功
            baseMapper.updateSpuStatus(spuId, ProductConstant.StatusEnum.SPU_UP.getCode());
        }else{
            //TODO 7、重复调用？接口幂等性；重试机制？以后再做
            log.error("es wrong");
        }
    }
}




