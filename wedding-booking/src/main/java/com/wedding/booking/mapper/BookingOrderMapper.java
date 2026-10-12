package com.wedding.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wedding.booking.module.BookingOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BookingOrderMapper extends BaseMapper<BookingOrder> {
}
