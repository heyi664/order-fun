package com.heyee.comments.mapper;

import com.heyee.comments.entity.VoucherOrder;
import com.heyee.comments.dto.TokenPackageOrderDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

    List<TokenPackageOrderDTO> queryTokenOrders(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(quantity), 0) FROM tb_voucher_order WHERE user_id = #{userId} AND voucher_id = #{voucherId} AND status < 4")
    Long sumQuantityByUserVoucher(@Param("userId") Long userId, @Param("voucherId") Long voucherId);

}
